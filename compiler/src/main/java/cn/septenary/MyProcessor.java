package cn.septenary;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.tools.Diagnostic.Kind.ERROR;

// 注解 @AutoService 自动生成 javax.annotation.processing.Processor 文件
@AutoService(Processor.class)
public class MyProcessor extends AbstractProcessor {

    private static final String ANNOTATION = "@" + MyAnnotation.class.getSimpleName();

    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(MyAnnotation.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<AnnotatedClass> annotatedClasses = new ArrayList<>();
        for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(MyAnnotation.class)) {
            if (annotatedElement instanceof TypeElement) {
                // Our annotation is defined with @Target(value=TYPE)
                TypeElement element = (TypeElement) annotatedElement;
                if (!isValidClass(element)) {
                    return true;
                }
                try {
                    AnnotatedClass annotatedClass = buildAnnotatedClass(element);
                    annotatedClasses.add(annotatedClass);
                } catch (NoPackageNameException | IOException e) {
                    String message = String.format("Couldn't process class %s: %s", element, e.getMessage());
                    messager.printMessage(ERROR, message, annotatedElement);
                }
            }
        }
        try {
            generate(annotatedClasses);
            // genHelloWorld();
        } catch (NoPackageNameException | IOException e) {
            messager.printMessage(ERROR, "Couldn't generate class");
        }
        Messager messager = processingEnv.getMessager();
        for (TypeElement te : annotations) {
            for (Element e : roundEnv.getElementsAnnotatedWith(te)) {
                messager.printMessage(Diagnostic.Kind.NOTE, "HelloProcessor Printing: " + e.toString());
            }
        }
        return true;
    }

    // 构建被 @MyAnnotation 注解的类
    private AnnotatedClass buildAnnotatedClass(TypeElement typeElement) throws NoPackageNameException, IOException {
        ArrayList<String> variableNames = new ArrayList<>();
        for (Element element : typeElement.getEnclosedElements()) {
            if (!(element instanceof VariableElement)) {
                continue;
            }
            VariableElement variableElement = (VariableElement) element;
            variableNames.add(variableElement.getSimpleName().toString());
        }
        return new AnnotatedClass(typeElement, variableNames);
    }

    // 生成 StringUtil 源代码
    private void generate(List<AnnotatedClass> list) throws NoPackageNameException, IOException {
        if (list.size() == 0) {
            return;
        }
        for (AnnotatedClass annotatedClass : list) {
            // debug
            String message = annotatedClass.annotatedClassName + " / " + annotatedClass.typeElement + " / " + Arrays.toString(annotatedClass.variableNames.toArray());
            messager.printMessage(Diagnostic.Kind.NOTE, message, annotatedClass.typeElement);
        }

        // 生成源代码
        String packageName = getPackageName(processingEnv.getElementUtils(), list.get(0).typeElement);
        TypeSpec generatedClass = CodeGenerator.generateClass(list);
        JavaFile javaFile = JavaFile.builder(packageName, generatedClass).build();

        // 在 app module/build/generated/source/apt 生成一份源代码
        javaFile.writeTo(processingEnv.getFiler());

        // 测试在桌面生成一份源代码
        javaFile.writeTo(new File(System.getProperty("user.home") + "/Desktop/"));
    }

    // 在桌面生成 HelloWorld.java
    private void genHelloWorld() throws IOException {
        MethodSpec main = MethodSpec.methodBuilder("main").addModifiers(Modifier.PUBLIC, Modifier.STATIC).returns(void.class).addParameter(String[].class, "args").addStatement("$T.out.println($S)", System.class, "Hello, JavaPoet!").build();
        TypeSpec helloWorld = TypeSpec.classBuilder("HelloWorld").addModifiers(Modifier.PUBLIC, Modifier.FINAL).addMethod(main).build();
        JavaFile javaFile = JavaFile.builder("cn.septenary.annotation", helloWorld).build();
        javaFile.writeTo(new File(System.getProperty("user.home") + "/Desktop/Hello"));
    }

    // 被 @MyAnnotation 注解的类
    private static class AnnotatedClass {
        // 整个类元素
        public final TypeElement typeElement;
        // 类名
        public final String annotatedClassName;
        // 成员变量
        public final List<String> variableNames;

        public AnnotatedClass(TypeElement typeElement, List<String> variableNames) {
            this.annotatedClassName = typeElement.getSimpleName().toString();
            this.variableNames = variableNames;
            this.typeElement = typeElement;
        }

        public TypeMirror getType() {
            return typeElement.asType();
        }
    }

    // 源码生成器
    private static class CodeGenerator {

        private static final String CLASS_NAME = "StringUtil";

        // 构建类
        public static TypeSpec generateClass(List<AnnotatedClass> classes) {
            TypeSpec.Builder builder = classBuilder(CLASS_NAME).addModifiers(PUBLIC, FINAL);
            for (AnnotatedClass anno : classes) {
                builder.addMethod(makeCreateStringMethod(anno));
            }
            return builder.build();
        }

        // 将 AnnotatedClass 作为参数构建 createString() 方法
        private static MethodSpec makeCreateStringMethod(AnnotatedClass annotatedClass) {
            StringBuilder builder = new StringBuilder();
            builder.append(String.format("return \"%s{\" + ", annotatedClass.annotatedClassName));
            for (String variableName : annotatedClass.variableNames) {
                builder.append(String.format(" \"%s='\" + String.valueOf(instance.%s) + \"',\" + ", variableName, variableName));
            }
            builder.append("\"}\"");
            return methodBuilder("createString").addJavadoc("@return string suitable for {@param instance}'s toString()").addModifiers(PUBLIC, STATIC).addParameter(TypeName.get(annotatedClass.getType()), "instance").addStatement(builder.toString()).returns(String.class).build();
        }
    }

    private boolean isPublic(TypeElement element) {
        return element.getModifiers().contains(PUBLIC);
    }

    private boolean isAbstract(TypeElement element) {
        return element.getModifiers().contains(ABSTRACT);
    }

    private boolean isValidClass(TypeElement element) {

        if (!isPublic(element)) {
            String message = String.format("Classes annotated with %s must be public.", ANNOTATION);
            messager.printMessage(Diagnostic.Kind.ERROR, message, element);
            return false;
        }

        if (isAbstract(element)) {
            String message = String.format("Classes annotated with %s must not be abstract.", ANNOTATION);
            messager.printMessage(Diagnostic.Kind.ERROR, message, element);
            return false;
        }

        return true;
    }

    private String getPackageName(Elements elements, TypeElement typeElement) throws NoPackageNameException {
        PackageElement pkg = elements.getPackageOf(typeElement);
        if (pkg.isUnnamed()) {
            throw new NoPackageNameException(typeElement);
        }
        return pkg.getQualifiedName().toString();
    }

}
