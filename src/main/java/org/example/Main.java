package org.example;

import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final long VALID_TO_TIME = 4102415999000L;
    private static final String LICENSE_KEY = "0000-0000-0000-0000";
    private static final String FILES_DIR = "files";
    private static final String CFR_JAR = "cfr-0.152.jar";
    private static final String OUTPUT_FILE = "a.txt";
    private static final String SEARCH_KEYWORD = "@SerializedName(value=\"validTo\")";

    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.println("请输入MyBatisCodeHelperPro的jar包完整路径：");
            String originalJarPath = scanner.nextLine().trim();

            // 检查文件是否为jar
            if (!originalJarPath.endsWith(".jar")) {
                System.err.println("指定的文件不是jar包！请输入有效的jar文件路径。");
                return;
            }

            // 检查文件是否存在
            File originalJarFile = new File(originalJarPath);
            if (!originalJarFile.exists() || !originalJarFile.isFile()) {
                System.err.println("指定的jar文件不存在！请检查路径是否正确。");
                return;
            }

            // 确保files目录存在
            File filesDir = new File(FILES_DIR);
            if (!filesDir.exists()) {
                filesDir.mkdirs();
                System.out.println("创建files目录: " + filesDir.getAbsolutePath());
            }

            // 获取jar文件名
            String jarFileName = originalJarFile.getName();
            File localJarFile = new File(FILES_DIR, jarFileName);
            File backupJarFile = new File(FILES_DIR, jarFileName + ".origin");

            // 复制jar文件到files目录
            Files.copy(originalJarFile.toPath(), localJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("已将jar包复制到: " + localJarFile.getAbsolutePath());

            // 创建原始文件备份
            Files.copy(localJarFile.toPath(), backupJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("已创建原始jar包备份: " + backupJarFile.getAbsolutePath());

            // 执行反编译命令
            decompileJarFile(jarFileName);

            // 分析反编译结果，查找目标类
            String targetClass = findTargetClass();
            if (targetClass == null) {
                System.err.println("找不到包含" + SEARCH_KEYWORD + "的目标类！");
                cleanupFiles(jarFileName, false);
                return;
            }
            System.out.println("找到目标类: " + targetClass);

            // 从jar包中提取目标类文件
            File extractedClassFile = extractClassFromJar(jarFileName, targetClass);
            if (extractedClassFile == null) {
                System.err.println("无法从jar包中提取目标类文件！");
                cleanupFiles(jarFileName, false);
                return;
            }

            // 验证提取的文件是否存在
            if (!extractedClassFile.exists()) {
                System.err.println("提取的类文件不存在: " + extractedClassFile.getAbsolutePath());
                cleanupFiles(jarFileName, false);
                return;
            }

            // 修改目标类文件
            String classPath = new File(FILES_DIR).getAbsolutePath();
            System.out.println("使用ClassPath: " + classPath);
            modifyClassFile(targetClass, classPath);

            // 替换修改后的类文件回jar包
            replaceClassInJar(jarFileName, targetClass);

            // 将修改后的jar包复制回原始位置
            try {
                Files.copy(localJarFile.toPath(), originalJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("已将破解后的jar包复制回原始位置: " + originalJarFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("复制破解后的jar包时出错: " + e.getMessage());
                System.err.println("请手动将 " + localJarFile.getAbsolutePath() + " 复制到 " + originalJarFile.getAbsolutePath());
                e.printStackTrace();
            }

            // 验证破解是否成功
            boolean successfullyUpdated = verifyJarFileUpdated(originalJarFile, targetClass);
            if (!successfullyUpdated) {
                System.err.println("警告：无法验证破解是否成功！请手动验证破解结果。");
            }

            // 清理临时文件
            cleanupFiles(jarFileName, true);

            System.out.println("成功完成所有操作！破解已完成！");
            System.out.println("备份文件保存在: " + backupJarFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("程序执行错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void cleanupFiles(String jarFileName, boolean success) {
        try {
            System.out.println("正在清理临时文件...");

            // 删除临时jar文件
            File localJarFile = new File(FILES_DIR, jarFileName);
            if (localJarFile.exists()) {
                localJarFile.delete();
            }

            // 删除临时生成的a.txt文件
            File outputFile = new File(FILES_DIR, OUTPUT_FILE);
            if (outputFile.exists()) {
                outputFile.delete();
            }

            // 删除temp jar文件
            File tempJarFile = new File(FILES_DIR, "temp_" + jarFileName);
            if (tempJarFile.exists()) {
                tempJarFile.delete();
            }

            // 删除提取出的类文件目录
            deleteExtractedClassDirectories();

            System.out.println("清理文件完成");
        } catch (Exception e) {
            System.err.println("清理文件时出错: " + e.getMessage());
        }
    }

    private static void deleteExtractedClassDirectories() {
        // 删除从jar包中提取的类文件目录
        File filesDir = new File(FILES_DIR);
        for (File file : filesDir.listFiles()) {
            if (file.isDirectory() && !file.getName().equals(".") && !file.getName().equals("..")) {
                deleteDirectory(file);
            }
        }
    }

    private static void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    private static void decompileJarFile(String jarFileName) throws IOException, InterruptedException {
        String command = String.format("java -jar %s/%s %s/%s --renamedupmembers true --hideutf false >> %s/%s",
                FILES_DIR, CFR_JAR, FILES_DIR, jarFileName, FILES_DIR, OUTPUT_FILE);

        // 确保a.txt为空
        Files.write(Paths.get(FILES_DIR, OUTPUT_FILE), new byte[0]);

        Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", command});
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("反编译命令执行失败，退出码: " + exitCode);
        }

        System.out.println("反编译完成，输出到: " + FILES_DIR + "/" + OUTPUT_FILE);
    }

    private static String findTargetClass() throws IOException {
        Path outputPath = Paths.get(FILES_DIR, OUTPUT_FILE);
        List<String> lines = Files.readAllLines(outputPath);

        Pattern packagePattern = Pattern.compile("package\\s+([\\w.]+);");
        Pattern classPattern = Pattern.compile("(class|interface)\\s+(\\w+)");

        String currentPackage = null;
        String currentClass = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            Matcher packageMatcher = packagePattern.matcher(line);
            if (packageMatcher.find()) {
                currentPackage = packageMatcher.group(1);
            }

            Matcher classMatcher = classPattern.matcher(line);
            if (classMatcher.find()) {
                currentClass = classMatcher.group(2);
            }

            if (line.contains(SEARCH_KEYWORD) && currentPackage != null && currentClass != null) {
                return currentPackage + "." + currentClass;
            }
        }

        return null;
    }

    private static File extractClassFromJar(String jarFileName, String targetClass) throws IOException {
        String classFilePath = targetClass.replace('.', '/') + ".class";
        // 将类文件提取到对应的包路径下
        File outputDir = new File(FILES_DIR + "/origin_class");
        File packageDir = new File(outputDir, targetClass.substring(0, targetClass.lastIndexOf('.')).replace('.', '/'));
        packageDir.mkdirs();

        File outputFile = new File(packageDir, targetClass.substring(targetClass.lastIndexOf('.') + 1) + ".class");

        try (JarFile jarFile = new JarFile(new File(FILES_DIR, jarFileName))) {
            JarEntry entry = jarFile.getJarEntry(classFilePath);
            if (entry == null) {
                System.err.println("在jar包中找不到类文件: " + classFilePath);
                return null;
            }

            try (InputStream is = jarFile.getInputStream(entry);
                 FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
        }

        System.out.println("从jar包中提取类文件: " + outputFile.getAbsolutePath());
        return outputFile;
    }

    private static void modifyClassFile(String targetClass, String classPath) throws Exception {
        // 用于存储字段名与其注解值的映射
        Map<String, List<String>> fieldAnnotationMap = new HashMap<>();

        ClassPool pool = ClassPool.getDefault();
        // 添加当前工作目录和files目录到类路径
        pool.insertClassPath(".");
        pool.insertClassPath(classPath);

        CtClass cc = null;
        String classFileName = classPath + "/origin_class" + "/" +
                    targetClass.substring(0, targetClass.lastIndexOf('.')).replace('.', '/') +
                    "/" + targetClass.substring(targetClass.lastIndexOf('.') + 1) + ".class";

            try (FileInputStream fis = new FileInputStream(classFileName)) {
                cc = pool.makeClass(fis);
                System.out.println("成功从文件加载类: " + classFileName);
            }

        if (cc == null) {
            throw new NotFoundException("无法加载类: " + targetClass);
        }

        // 首先遍历所有字段，记录注解信息
        for (CtField field : cc.getDeclaredFields()) {
            AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute)
                    field.getFieldInfo().getAttribute(AnnotationsAttribute.visibleTag);

            if (annotationsAttribute != null) {
                for (Annotation annotation : annotationsAttribute.getAnnotations()) {
                    if (annotation.getTypeName().endsWith("SerializedName")) {
                        String annotationValue = annotation.getMemberValue("value").toString().replaceAll("\"", "");
                        fieldAnnotationMap.computeIfAbsent(field.getName(), k -> new ArrayList<>()).add(annotationValue);
                        System.out.println("Field: " + field.getName() + ", Annotation value: " + annotationValue);
                    }
                }
            }
        }

        // 然后修改对应的方法
        for (CtMethod method : cc.getDeclaredMethods()) {
            modifyMethod(method, fieldAnnotationMap);
        }

        // 保存修改后的类文件到对应的包目录
        cc.writeFile(classPath + "/cracked_class");
        System.out.println("类文件已成功修改");
    }

    private static void modifyMethod(CtMethod method, Map<String, List<String>> fieldAnnotationMap) throws Exception {
        String methodName = method.getName();
        CtClass[] params = method.getParameterTypes();
        CtClass returnType = method.getReturnType();

        // 只处理getter方法（无参数方法）
        if (params.length == 0) {
            String returnTypeName = returnType.getName();

            // 遍历所有字段的注解值
            for (Map.Entry<String, List<String>> entry : fieldAnnotationMap.entrySet()) {
                String fieldName = entry.getKey();
                List<String> annotationValues = entry.getValue();

                // 根据返回类型和方法名匹配对应的getter
                if (methodMatchesField(method, fieldName, returnTypeName)) {
                    for (String annotationValue : annotationValues) {
                        switch (annotationValue) {
                            case "validTo":
                                if (returnTypeName.equals("java.lang.Long")) {
                                    method.setBody("return Long.valueOf(" + VALID_TO_TIME + "L);");
                                    System.out.println("Modified validTo getter");
                                }
                                break;
                            case "paidKey":
                                if (returnTypeName.equals("java.lang.String")) {
                                    method.setBody("return \"" + LICENSE_KEY + "\";");
                                    System.out.println("Modified paidKey getter");
                                }
                                break;
                            case "valid":
                                if (returnTypeName.equals("java.lang.Boolean")) {
                                    method.setBody("return Boolean.TRUE;");
                                    System.out.println("Modified valid getter");
                                }
                                break;
                        }
                    }
                }
            }
        }
    }

    private static boolean methodMatchesField(CtMethod method, String fieldName, String returnTypeName) {
        // 在这里实现方法名和字段的匹配逻辑
        // 由于原代码中使用a和b作为方法名，我们需要根据具体情况匹配
        String methodName = method.getName();

        // 假设方法名为a的可能是paidKey、valid、validTo的getter
        if (methodName.equals("a")) {
            return (fieldName.equals("a") &&
                    (returnTypeName.equals("java.lang.String") ||
                            returnTypeName.equals("java.lang.Boolean") ||
                            returnTypeName.equals("java.lang.Long")));
        }
        // 假设方法名为b的是userMac的getter
        else if (methodName.equals("b")) {
            return fieldName.equals("b") && returnTypeName.equals("java.lang.String");
        }

        return false;
    }

    private static void replaceClassInJar(String jarFileName, String targetClass) throws IOException {
        String classFilePath = targetClass.replace('.', '/') + ".class";
        // 获取提取出的修改后的类文件路径
        File extractedClassFile = new File(FILES_DIR + "/cracked_class" + "/" +
                targetClass.substring(0, targetClass.lastIndexOf('.')).replace('.', '/') + "/" +
                targetClass.substring(targetClass.lastIndexOf('.') + 1) + ".class");
        File tempJarFile = new File(FILES_DIR, "temp_" + jarFileName);
        File originalJarFile = new File(FILES_DIR, jarFileName);

        try {
            // 验证修改后的类文件是否存在
            if (!extractedClassFile.exists()) {
                throw new IOException("修改后的类文件不存在: " + extractedClassFile.getAbsolutePath());
            }

            // 创建临时jar文件
            Files.copy(originalJarFile.toPath(), tempJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            String command = String.format("cd %s && jar uvf %s %s",
                    FILES_DIR + "/cracked_class" , "../" + jarFileName, classFilePath);

            Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", command});

            // 打印命令执行的错误输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.err.println("命令错误: " + line);
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IOException("更新jar包命令执行失败，退出码: " + exitCode);
            }

            System.out.println("成功将修改后的类文件替换回jar包");
        } catch (Exception e) {
            System.err.println("替换jar包中的类文件失败: " + e.getMessage());
            e.printStackTrace();

            // 恢复原始jar
            if (tempJarFile.exists()) {
                Files.copy(tempJarFile.toPath(), originalJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("已恢复原始jar文件");
            }
            throw new IOException("更新jar包失败", e);
        } finally {
            // 临时文件在cleanupFiles中统一删除
        }
    }

    private static boolean verifyJarFileUpdated(File jarFile, String targetClass) {
        try {
            String classFilePath = targetClass.replace('.', '/') + ".class";
            try (JarFile jar = new JarFile(jarFile)) {
                JarEntry entry = jar.getJarEntry(classFilePath);
                if (entry == null) {
                    System.err.println("在更新后的jar文件中找不到类: " + classFilePath);
                    return false;
                }
                return true;
            }
        } catch (Exception e) {
            System.err.println("验证更新的jar文件时出错: " + e.getMessage());
            return false;
        }
    }
}
