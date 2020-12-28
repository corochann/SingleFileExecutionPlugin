# SingleFileExecutionPlugin
[CLion](https://www.jetbrains.com/clion/) plugin to execute single file .c/.cpp file quickly.

## Description
CLion is a C/C++ IDE on [IntelliJ IDEA](https://www.jetbrains.com/idea/) platform provided by [JetBrains](https://www.jetbrains.com/).

CLion is working on CMake platform, so when you want to run a single file with main() function you need to configure CMakeLists.txt file everytime.
This plugin helps you to add a configuration to quickly run a single .c/.cpp file.

Links

 - **[Document page](http://corochann.com/projects/single-file-execution-plugin)**: for more detail information.
 - **[Official plugin page](https://plugins.jetbrains.com/plugin/8352?pr=)**

<h2>Installation</h2>
<p>C/C++ Single File Execution plugin is uploaded on JetBrains repositry, so you can download by navigating [File] → [Settings] → [Plugins] tab → click [Browse repositries...] in CLion.</p>
<p>You can find and install C/C++ Single File Execution plugin.</p>
<p><a href="https://plugins.jetbrains.com/files/8352/screenshot_15874.png"><img class="aligncenter size-full wp-image-939" src="https://plugins.jetbrains.com/files/8352/screenshot_15874.png" alt="install_form_repositry" width="836" height="701" /></a></p>
<p>&nbsp;</p>
<h2>How to use</h2>
<ol>
<li>Create or show C/C++ source code you want to run on the editor.</li>
<li><strong>Right click</strong> on the editor.</li>
<li>Select "Add executable for single c/cpp file".</li>
</ol>
<p>That's all! Plugin automatically insert <code>add_executable</code> to <code>CMakeLists.txt</code> with proper path for you.</p>
<p><a href="https://plugins.jetbrains.com/files/8352/screenshot_15875.png"><img class="aligncenter size-large wp-image-945" src="https://plugins.jetbrains.com/files/8352/screenshot_15875.png" alt="sample_example2" width="700" height="335" /></a></p>
<p>After that, you can start coding and once it's done, run it by selecting proper executable name on the top-right panel in CLion.</p>
<p><a href="https://plugins.jetbrains.com/files/8352/screenshot_15878.png"><img class="aligncenter size-large wp-image-946" src="https://plugins.jetbrains.com/files/8352/screenshot_15878.png" alt="sample_example_run_bar" width="700" height="336" /></a></p>
<h2>Configuration</h2>
<p><a href="https://plugins.jetbrains.com/files/8352/screenshot_15900.png"><img class="aligncenter size-large wp-image-958" src="https://plugins.jetbrains.com/files/8352/screenshot_15900.png" alt="single_file_execution_settings_v120" width="700" height="471" /></a></p>
<p>Little configuration is available from [File]  → [Settings] → [Tools] tab &gt; [Single File Execution Plugin].</p>
<h3>executable name</h3>
<p>You may specify the executable name. As a default with <code>%FILENAME%</code>, it will use a name depending on the source code file name. In this case, every time you add a new source code and insert <code>add_executable</code>, new executable name will be added. Build configuration tab can be messy in this case as the number of files increases.</p>
<p>Another way is to use "fixed" executable name here (not use <code>%FILENAME%</code>), in that case you can always run single source code file with same executable name.</p>
<h3>runtime output directory</h3>
<p>You can also specify a directory where the executable file will be stored after build.</p>
<p>For example, when you set <code>%FILEDIR%</code>, executable file will be located in the same directory of source file. This configuration is especially useful when your source code reads/writes another file which is located in same directory of the source file.</p>
<p>Concrete example is for programming contest. You may want to read the input data from another text file. You can just place the input data text file in the same directory of source file, and just write below</p>
<pre class="lang:c++ decode:true">int main() {
    freopen("input_data.txt", "r", stdin);

    ...

}</pre>
<p>you can now build and run this program by pressing "run" button on CLion.</p>
