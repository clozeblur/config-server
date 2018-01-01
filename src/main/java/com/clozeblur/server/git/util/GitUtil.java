package com.clozeblur.server.git.util;

import com.clozeblur.server.git.exception.GitParseException;
import com.clozeblur.server.git.model.BranchConfig;
import com.clozeblur.server.git.model.Difference;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.util.Assert;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by clozeblur
 * on 2017/6/26
 */
public class GitUtil {

    private GitUtil() {
    }

    /**
     * 连接git
     *
     * @param remoteGitPath 远程git地址
     * @param username      用户名
     * @param password      密码
     * @return git对象
     */
    public static Git connect(String remoteGitPath, String localGitPath, String username, String password) {
        try {
            preDeleteFiles(localGitPath);
            return Git
                    .cloneRepository()
                    .setURI(remoteGitPath)
                    .setDirectory(new File(localGitPath))
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                    .call();
        } catch (GitAPIException e) {
            throw new GitParseException(e.getMessage());
        }
    }

    public static Map<String, BranchConfig> compare(Map<String, AbstractTreeIterator> oldVal,
                                                    Map<String, AbstractTreeIterator> newVal,
                                                    Git git) {

        Map<String, BranchConfig> branchConfigMap = new HashMap<>();
        for (String branch : oldVal.keySet()) {
            try {
                BranchConfig branchConfig = compareCommitDifference(oldVal.get(branch), newVal.get(branch), git, branch);
                branchConfigMap.put(branch, branchConfig);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return branchConfigMap;
    }

    /**
     * 输出difference
     *
     * @param oldTree 旧的tree
     * @param newTree 新的tree
     * @param git     git对象
     * @return 差异对象
     * @throws GitAPIException git操作异常
     */
    private static BranchConfig compareCommitDifference(AbstractTreeIterator oldTree,
                                                        AbstractTreeIterator newTree,
                                                        Git git, String branch) throws Exception {
        List<DiffEntry> diff = git.diff()
                .setOldTree(oldTree)
                .setNewTree(newTree)
                .setShowNameAndStatusOnly(true)
                .call();

        Repository repo = git.getRepository();
        List<Ref> refs = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
        String version = null;
        for (Ref ref : refs) {
            if (branch.equals(ref.getName().replace("refs/remotes/origin/", ""))) {
                ObjectId objectId = ref.getObjectId();
                RevWalk walk = new RevWalk(repo);
                RevCommit root = walk.parseCommit(objectId);
                version = root.getName();
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DiffFormatter df = new DiffFormatter(out);
        //设置比较器为忽略空白字符对比（Ignores all whitespace）
        df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
        df.setRepository(git.getRepository());
        List<Difference> differences = new ArrayList<>();
        //每一个diffEntry都是第个文件版本之间的变动差异
        for (DiffEntry diffEntry : diff) {
            Difference difference = new Difference();
            try {
                df.format(diffEntry);
            } catch (IOException e) {
                throw new GitParseException();
            }
            String diffText;
            try {
                diffText = out.toString("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new GitParseException();
            }
            difference.setText(diffText);

            //获取文件差异位置，从而统计差异的行数，如增加行数，减少行数
            FileHeader fileHeader;
            try {
                fileHeader = df.toFileHeader(diffEntry);
            } catch (IOException e) {
                throw new GitParseException();
            }
            Assert.notNull(fileHeader, "获取fileHeader为null");
            @SuppressWarnings({"unchecked", "deprecated"})
            List<HunkHeader> hunks = (List<HunkHeader>) fileHeader.getHunks();
            int addSize = 0;
            int subSize = 0;
            for (HunkHeader hunkHeader : hunks) {
                EditList editList = hunkHeader.toEditList();
                for (Edit edit : editList) {
                    subSize += edit.getEndA() - edit.getBeginA();
                    addSize += edit.getEndB() - edit.getBeginB();

                }
            }
            difference.setAddSize(addSize);
            difference.setSubSize(subSize);
            differences.add(difference);
            out.reset();
        }
        BranchConfig branchConfig = new BranchConfig();
        branchConfig.setDifferences(differences);
        branchConfig.setVersion(version);
        return branchConfig;
    }

    /**
     * 记录当前Tree
     *
     * @param git git对象
     * @return 当前Tree
     */
    public static Map<String, AbstractTreeIterator> getCurrentTree(Git git) throws GitParseException {
        Map<String, AbstractTreeIterator> treeMap = new HashMap<>();
        try {
            List<Ref> refs = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
            Repository repo = git.getRepository();
            for (Ref ref : refs) {
                String branch = ref.getName().replace("refs/remotes/origin/", "");

                ObjectId objectId = ref.getObjectId();
                RevWalk walk = new RevWalk(repo);

                AbstractTreeIterator treeIterator;

                try {
                    RevCommit root = walk.parseCommit(objectId);
                    walk.sort(RevSort.COMMIT_TIME_DESC);
                    walk.markStart(root);
                    treeIterator = prepareTreeParser(walk.iterator().next(), walk, repo);
                } catch (IOException e) {
                    throw new GitParseException("IO异常");
                }

                treeMap.put(branch, treeIterator);

            }

        } catch (GitAPIException e) {
            throw new GitParseException("解析git异常");
        }

        return treeMap;
    }

    /**
     * 预解析Tree
     *
     * @param commit     某次commit
     * @param walk       所有的信息List
     * @param repository repo对象
     * @return tree
     */
    private static AbstractTreeIterator prepareTreeParser(RevCommit commit, RevWalk walk, Repository repository) {
        try {
            RevTree tree = walk.parseTree(commit.getTree().getId());
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try {
                ObjectReader oldReader = repository.newObjectReader();
                treeParser.reset(oldReader, tree.getId());
            } catch (Exception e) {
                throw new GitParseException(e.getMessage());
            }
            walk.dispose();
            return treeParser;
        } catch (Exception e) {
            throw new GitParseException(e.getMessage());
        }
    }

    /**
     * 预处理本地暂存项目文件,清空
     */
    private static void preDeleteFiles(String localGitPath) {
        File repoFile = new File(localGitPath);
        FileUtil.deleteContents(repoFile);
    }

    /**
     * Copy from org.aspectj.util.FileUtil
     */
    static class FileUtil {

        /** accept all files */
        static final FileFilter ALL = f -> true;

        /**
         * Recursively delete the contents of dir, but not the dir itself
         *
         * @return the total number of files deleted
         */
        static int deleteContents(File dir) {
            return deleteContents(dir, ALL);
        }

        /**
         * Recursively delete some contents of dir, but not the dir itself. This deletes any subdirectory which is empty after its files
         * are deleted.
         *
         * @return the total number of files deleted
         */
        static int deleteContents(File dir, FileFilter filter) {
            return deleteContents(dir, filter, true);
        }

        /**
         * Recursively delete some contents of dir, but not the dir itself. If deleteEmptyDirs is true, this deletes any subdirectory
         * which is empty after its files are deleted.
         *
         * @param dir the File directory (if a file, the the file is deleted)
         * @return the total number of files deleted
         */
        static int deleteContents(File dir, FileFilter filter,
                                  boolean deleteEmptyDirs) {
            if (null == dir) {
                throw new IllegalArgumentException("null dir");
            }
            if ((!dir.exists()) || (!dir.canWrite())) {
                return 0;
            }
            if (!dir.isDirectory()) {
                dir.delete();
                return 1;
            }
            String[] fromFiles = dir.list();
            if (fromFiles == null) {
                return 0;
            }
            int result = 0;
            for (String string : fromFiles) {
                File file = new File(dir, string);
                if ((null == filter) || filter.accept(file)) {
                    if (file.isDirectory()) {
                        result += deleteContents(file, filter, deleteEmptyDirs);
                        String[] fileContent = file.list();
                        if (deleteEmptyDirs && fileContent != null
                                && 0 == fileContent.length) {
                            file.delete();
                        }
                    } else {
                    /* boolean ret = */
                        file.delete();
                        result++;
                    }
                }
            }
            return result;
        }
    }
}
