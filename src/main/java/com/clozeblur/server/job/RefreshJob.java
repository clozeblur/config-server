package com.clozeblur.server.job;

import com.alibaba.fastjson.JSONObject;
import com.clozeblur.server.git.configuration.Repo;
import com.clozeblur.server.git.model.BranchConfig;
import com.clozeblur.server.git.model.Difference;
import com.clozeblur.server.git.util.GitUtil;
import com.clozeblur.server.redismq.MessageBody;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author clozeblur
 * <html>更新配置信息的任务与redis推送最新配置的事务</html>
 */
@Component
public class RefreshJob {

    private static final Logger logger = LoggerFactory.getLogger(RefreshJob.class);

    private static ConcurrentHashMap<String, MessageBody> tempMessages = new ConcurrentHashMap<>();

    private static Map<String, Map<String, String>> repos;

    @Value("${spring.cloud.config.server.git.username:configServerUser}")
    private String username;

    @Value("${spring.cloud.config.server.git.password:configServerUser}")
    private String password;

    @Value("${spring.cloud.config.server.git.uri}")
    private String uri;

    @Value("${spring.profiles.active:dev}")
    private String env;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private Repo repo;

    @PostConstruct
    public void init() {
        logger.info("tree初始化开始");
        try {
            repos = repo.getRepos();
            // 如果本地目录为空,则预加载;   如果本地目录有文件,则跳过
            // (这么做的目的是防止本服务挂掉后,到下次重启时中间有配置更新而不能正确告知客户端读取最新信息)
            preCheck(Constants.LOCAL_PATH);
            preCheck(Constants.LOCAL_PATH_SECRET);
        } catch (Exception e) {
            logger.error("tree初始化失败: {}", e.getMessage());
        }

        logger.info("tree初始化成功");
    }

    /**
     * 先验本地仓库路径下,文件格式是否合法.
     * 不合法的时候就重新拉代码:意味着服务端是第一次启动;
     * 若合法则跳过:意味着服务端是一次重启
     * @param path 本地仓库路径
     */
    private void preCheck(String path) {
        File file = new File(path);
        File[] subFiles = file.listFiles();
        boolean needPreDeal = false;
        if (subFiles == null || subFiles.length == 0) {
            needPreDeal = true;
        } else {
            // 如果本地目录内文件不合法(不符合配置文件命名规则,则同样进行预加载)
            boolean illegal = false;
            for (File f : subFiles) {
                // 如果为目录,则继续查询其子文件
                if (f.isDirectory()) {
                    File[] minFiles = f.listFiles();
                    if (minFiles == null || minFiles.length == 0) continue;
                    for (File min : minFiles) {
                        // 判断子文件是否满足命名规则
                        String name = min.getName();
                        illegal = judgeIllegal(name);
                    }
                } else {
                    // 不为目录,直接验证规则
                    String name = f.getName();
                    illegal = judgeIllegal(name);
                }
            }
            if (illegal) {
                needPreDeal = true;
            }
        }
        if (needPreDeal) {
            // 第一次启动,先清空本地并拉取配置
            if (path.equals(Constants.LOCAL_PATH)) {
                preDealInfoGit();
            } else if (path.equals(Constants.LOCAL_PATH_SECRET)) {
                preDealSecretGits();
            }
        } else {
            // 是一次重启,直接判断版本是否更新
            watchMainTree();
            watchSecretTree();
        }
    }

    private boolean judgeIllegal(String name) {
        return !(name.endsWith(".properties")
                || name.endsWith(".yml")
                || name.endsWith(".gitigonre")
                || name.endsWith(".yaml"));
    }

    /**
     * 初始化config-repo的信息
     */
    private void preDealInfoGit() {
        GitUtil.connect(uri, Constants.LOCAL_PATH, username, password);
    }

    /**
     * 初始化info目录下各repo的信息
     */
    private void preDealSecretGits() {
        for (String branch : repos.keySet()) {
            Map<String, String> detail = repos.get(branch);
            String uri = detail.get(Constants.URI);
            String username = detail.get(Constants.USERNAME);
            String password = detail.get(Constants.PASSWORD);
            GitUtil.connect(uri, Constants.LOCAL_PATH_SECRET + "-" + branch, username, password);
        }
    }

    /**
     * 监听父仓库
     */
    @Scheduled(cron = "0/20 * * * * ?")
    public void watchMainTree() {
        try {
            // 本地仓库当前版本,也就是远程git的上个版本
            Git olderGit = Git.open(new File(Constants.LOCAL_PATH));
            Map<String, AbstractTreeIterator> olderTrees = GitUtil.getCurrentTree(olderGit);

            // 远程git当前版本,也是最新的版本
            Git newlyGit = GitUtil.connect(uri, Constants.LOCAL_PATH, username, password);
            Map<String, AbstractTreeIterator> newlyTrees = GitUtil.getCurrentTree(newlyGit);

            // 比较得到版本差异
            Map<String, BranchConfig> differences = GitUtil.compare(olderTrees, newlyTrees, newlyGit);

            analyze(differences, null);
        } catch (IOException e) {
            logger.error("读取本地git仓库异常: {}", Constants.LOCAL_PATH);
        }
    }

    /**
     * 监听子仓库
     */
    @Scheduled(cron = "0/60 * * * * ?")
    public void watchSecretTree() {
        for (String branch : repos.keySet()) {
            String uri = repos.get(branch).get(Constants.URI);
            String username = repos.get(branch).get(Constants.USERNAME);
            String password = repos.get(branch).get(Constants.PASSWORD);

            try {
                String localGitPath = Constants.LOCAL_PATH_SECRET + "-" + branch;
                // 本地仓库当前版本,也就是远程git的上个版本
                Git olderGit = Git.open(new File(localGitPath));
                Map<String, AbstractTreeIterator> olderTrees = GitUtil.getCurrentTree(olderGit);

                // 远程git当前版本,也是最新的版本
                Git newlyGit = GitUtil.connect(uri, localGitPath, username, password);
                Map<String, AbstractTreeIterator> newlyTrees = GitUtil.getCurrentTree(newlyGit);

                // 比较得到版本差异
                Map<String, BranchConfig> differences = GitUtil.compare(olderTrees, newlyTrees, newlyGit);

                // 获取其余git仓库的label信息
                String label = repos.get(branch).get(Constants.PATTERN).replace(Constants.PATTERN_SUFFIX, "").trim();

                analyze(differences, label);
            } catch (IOException e) {
                logger.error("读取本地git仓库异常: {}", Constants.LOCAL_PATH_SECRET + "-" + branch);
            }
        }
    }

    /**
     * 分析更新了哪些信息,一一比对,然后根据需要推送消息
     * @param differences 更新信息
     * @param defaultLabel 是否是主分支,用于区分父子仓库
     */
    @SuppressWarnings("unchecked")
    private void analyze(Map<String, BranchConfig> differences, String defaultLabel) {
        for (String branch : differences.keySet()) {

            // 若为默认仓库(config-repo)或secret仓库的master分支,则跳过这部分信息的比对
            if (!StringUtils.isEmpty(defaultLabel) && branch.equals("master")) continue;

            for (Difference diff : differences.get(branch).getDifferences()) {
                if (diff != null && !diff.toString().isEmpty()) {
                    // 跳过所有不符合的difference
                    String text = diff.getText();
                    if (text.contains(Constants.IGNORE)) continue;

                    String filename = getFileName(text);
                    if (StringUtils.isEmpty(filename)) continue;

                    String key = Constants.generateKey(filename, env, branch);
                    String shortName = filename.endsWith("-" + env) ?
                            filename.substring(0, filename.lastIndexOf("-" + env)) : filename;
                    String channel = branch + ":" + env + ":" + shortName;

                    if (diff.getAddSize() > 0) {
                        logger.info("======发现文件更新: {},分支位于 {} ======", filename, branch);
                        try {
                            Environment environment = environmentRepository.findOne(shortName, env, branch);
                            if (environment.getPropertySources() == null
                                    || environment.getPropertySources().size() == 0) continue;
                            String value = JSONObject.toJSONString(environment);
                            logger.info("更新缓存, key: {} value: {}", key, value);

                            Map<String, String> source = new HashMap<>();
                            for (PropertySource propertySource : environment.getPropertySources()) {
                                for (Object o : propertySource.getSource().keySet()) {
                                    source.put(o.toString(), propertySource.getSource().get(o).toString());
                                }
                            }
                            MessageBody body = new MessageBody(source, branch, env, shortName);

                            try {
                                stringRedisTemplate.convertAndSend(channel, body);
                            } catch (Exception e) {
                                tempMessages.put(channel, body);
                            }

                        } catch (Exception e) {
                            logger.error("======文件更新异常: {}", filename);
                        }
                    }
                    else if (diff.getAddSize() == 0 && diff.getSubSize() > 0) {
                        logger.info("发现文件删除: {},分支位于 {}", filename, branch);
                        // 应该不做处理比较好,为此发送一个空内容的消息给客户端捕捉,不更新但告知
                        MessageBody body = new MessageBody(null, branch, env, shortName);
                        stringRedisTemplate.convertAndSend(channel, body);
                    }
                }
            }
        }
    }

    /**
     * 通过difference的固定格式,找到更新的文件名
     * @param text difference文本
     * @return 文件名
     */
    private String getFileName(String text) {
        String filename = text.replace(Constants.TEXT_PREFIX, "");

        if (text.contains(Constants.PROPERTIES_SPLIT)) {
            filename = filename.split(Constants.PROPERTIES_SPLIT)[0].trim();
        }
        else if (text.contains(Constants.YML_SPLIT)) {
            filename = filename.split(Constants.YML_SPLIT)[0].trim();
        }
        else if (text.contains(Constants.YAML_SPLIT)) {
            filename = filename.split(Constants.YAML_SPLIT)[0].trim();
        }
        else {
            return "";
        }
        List<String> param = Arrays.asList(filename.split("/"));
        return param.get(param.size() - 1);
    }

    /**
     * redis断连接可能性下,需要将当前map中暂存的已更新文件内容再次尝试用消息发出去
     */
    @Scheduled(cron = "0 0 0/1 * * ?")
    public void retry() {
        // 如果没有暂存消息,则跳过
        if (CollectionUtils.isEmpty(tempMessages)) return;
        try {
            // 尝试发送一个测试消息,如果依然报错,则跳过
            stringRedisTemplate.convertAndSend("nobody:channel", "canSend");
        } catch (Exception e) {
            return;
        }
        // 全部发送出去
        for (String channel : tempMessages.keySet()) {
            stringRedisTemplate.convertAndSend(channel, tempMessages.get(channel));
        }
        // 清空
        destroy();
    }

    private void destroy() {
        tempMessages = new ConcurrentHashMap<>();
    }

    static class Constants {
        /**
         * common变量
         */
        static final String PATTERN_SUFFIX = "-secret-*";

        static final String TEXT_PREFIX = "diff --git a/";

        static final String IGNORE = "a/.gitignore b/.gitignore";

        static final String PROPERTIES_SPLIT = ".properties b/";

        static final String YML_SPLIT = ".yml b/";

        static final String YAML_SPLIT = ".yaml b/";

        static final String LOCAL_PATH = System.getProperty("java.io.tmpdir") + "/config";

        static final String LOCAL_PATH_SECRET = System.getProperty("java.io.tmpdir") + "/info-secret";

        static final String PATTERN = "pattern";

        static final String USERNAME = "username";

        static final String PASSWORD = "password";

        static final String URI = "uri";

        /**
         * 不标准存放在这里,只用于生成key的盐,最好从别处load进来
         */
        private static final String CONCAT = "##_##";

        private static final String LINK = "==-==";

        private static final String TAG = "configServer";
        /**
         * 生成存入redis的key
         *
         * @param name 文件名
         * @param env 环境名
         * @param branch 分支名
         * @return key值
         */
        static String generateKey(String name, String env, String branch) {
            return name + CONCAT + env + CONCAT + branch + TAG;
        }

        static String generateVersionKey(String isMain, String env, String branch) {
            return isMain + LINK + env + LINK + branch + TAG;
        }
    }
}
