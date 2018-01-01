package com.clozeblur.server.git;

/**
 * Created by clozeblur
 * on 2017/6/27
 */
@Deprecated
@SuppressWarnings("unused")
public class Template {

    /**
     * 通过GitUtils的compare方法得到的difference对象toString()方法所呈现出的三种情况结果样例
     *
     * diffTemplate1代表文件修改
     * diffTemplate2代表文件删除
     * diffTemplate3代表文件增加
     */

    private static final String diffTemplate1 = "Difference{text='diff --git a/testJGitFile1.properties b/testJGitFile1.properties\n" +
            "index 3762442..fdeb378 100644\n" +
            "--- a/testJGitFile1.properties\n" +
            "+++ b/testJGitFile1.properties\n" +
            "@@ -1 +1 @@\n" +
            "-test.content=1\n" +
            "\\ No newline at end of file\n" +
            "+test.content=4\n" +
            "\\ No newline at end of file\n" +
            "', addSize=1, subSize=1}";

    private static final String diffTemplate2 = "Difference{text='diff --git a/testJGitFile2.properties b/testJGitFile2.properties\n" +
            "deleted file mode 100644\n" +
            "index 42ed983..0000000\n" +
            "--- a/testJGitFile2.properties\n" +
            "+++ /dev/null\n" +
            "@@ -1 +0,0 @@\n" +
            "-test.content=2\n" +
            "\\ No newline at end of file\n" +
            "', addSize=0, subSize=1}";

    private static final String diffTemplate3 = "Difference{text='diff --git a/testJGitFile3.properties b/testJGitFile3.properties\n" +
            "new file mode 100644\n" +
            "index 0000000..0b5ae7a\n" +
            "--- /dev/null\n" +
            "+++ b/testJGitFile3.properties\n" +
            "@@ -0,0 +1 @@\n" +
            "+test.content=3\n" +
            "\\ No newline at end of file\n" +
            "', addSize=1, subSize=0}";
}
