package com.theuran.doclets;

import com.google.gson.*;
import com.google.gson.stream.JsonWriter;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.util.DocTrees;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

public class JsonDoclet implements Doclet {
    public static String spaceDelimiter = "  ";

    @Override
    public void init(Locale locale, Reporter reporter) {
    }

    @Override
    public String getName() {
        return "Json";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return Collections.emptySet();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_17;
    }

    @Override
    public boolean run(DocletEnvironment environment) {
        JsonObject object = new JsonObject();

        object.add("classes", JsonDoclet.compileClasses(environment));
        object.add("packages", JsonDoclet.compilePackages(environment));

        JsonDoclet.write(object);

        return true;
    }

    private static JsonElement compilePackages(DocletEnvironment environment) {
        JsonArray packages = new JsonArray();

        Set<PackageElement> packageElements = ElementFilter.packagesIn(environment.getSpecifiedElements());

        DocTrees docTrees = environment.getDocTrees();

        for (PackageElement packageDoc : packageElements) {
            JsonObject packageObject = new JsonObject();

            packageObject.addProperty("name", packageDoc.getQualifiedName().toString());

            DocCommentTree docComment = docTrees.getDocCommentTree(packageDoc);

            String comment = "";

            if (docComment != null)
                comment = docComment.getFullBody().stream().map(DocTree::toString).collect(Collectors.joining("\n"));

            if (!comment.trim().isEmpty()) {
                packageObject.addProperty("doc", comment);
            }

            packages.add(packageObject);
        }

        return packages;
    }

    private static JsonElement compileClasses(DocletEnvironment environment) {
        JsonArray array = new JsonArray();

        DocTrees docTrees = environment.getDocTrees();

        Set<TypeElement> classElement = ElementFilter.typesIn(environment.getIncludedElements());

        for (TypeElement element : classElement) {
            List<ExecutableElement> methods = ElementFilter.methodsIn(element.getEnclosedElements());

            if (!methods.isEmpty()) {
                JsonObject clazz = new JsonObject();

                JsonDoclet.compileClass(clazz, element, docTrees);

                array.add(clazz);
            }
        }

        return array;
    }

    private static void compileClass(JsonObject clazz, TypeElement element, DocTrees docTrees) {
        clazz.addProperty("name", element.getQualifiedName().toString());

        DocCommentTree docComment = docTrees.getDocCommentTree(element);

        String commentText = "";

        if (docComment != null)
            commentText = docComment.getFullBody().stream().map(DocTree::toString).collect(Collectors.joining("\n"));


        clazz.addProperty("doc", commentText);

        TypeMirror superClass = element.getSuperclass();

        if (superClass.getKind() != TypeKind.NONE && superClass.getKind() == TypeKind.DECLARED) {
            String superName = ((TypeElement) ((DeclaredType) superClass).asElement()).getQualifiedName().toString();

            clazz.addProperty("superclass", superName);
        }

        JsonArray array = new JsonArray();

        for (TypeMirror type : element.getInterfaces()) {
            if (type.getKind() == TypeKind.DECLARED) {
                String interfaceName = ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();

                array.add(new JsonPrimitive(interfaceName));
            }
        }

        clazz.add("interfaces", array);
        clazz.add("methods", JsonDoclet.compileMethods(
                ElementFilter.methodsIn(element.getEnclosedElements()).toArray(new ExecutableElement[0]),
                docTrees
        ));
    }

    private static JsonElement compileMethods(ExecutableElement[] methods, DocTrees docTrees) {
        JsonArray array = new JsonArray();

        for (ExecutableElement methodDoc : methods) {
            JsonObject method = new JsonObject();

            JsonDoclet.compileMethod(method, methodDoc, docTrees);

            array.add(method);
        }

        return array;
    }

    private static void compileMethod(JsonObject method, ExecutableElement methodDoc, DocTrees docTrees) {
        method.addProperty("name", methodDoc.getSimpleName().toString());

        DocCommentTree docComment = docTrees.getDocCommentTree(methodDoc);

        String commentText = "";

        if (docComment != null)
            commentText = docComment.getFullBody().stream().map(DocTree::toString).collect(Collectors.joining("\n"));

        method.addProperty("doc", commentText);
        method.add("returns", JsonDoclet.compileReturn(methodDoc, docComment));
        method.add("arguments", JsonDoclet.compileArguments(methodDoc, docComment));
        method.add("annotations", JsonDoclet.compileAnnotations(methodDoc, docComment));
    }

    private static JsonElement compileAnnotations(ExecutableElement methodDoc, DocCommentTree docComment) {
        JsonArray array = new JsonArray();

        for (AnnotationMirror annotationDesc : methodDoc.getAnnotationMirrors()) {
            TypeElement type = ((TypeElement) annotationDesc.getAnnotationType().asElement());

            array.add(new JsonPrimitive(type.getQualifiedName().toString()));
        }

        return array;
    }

    private static JsonElement compileArguments(ExecutableElement methodDoc, DocCommentTree docComment) {
        Map<String, String> tags = new HashMap<>();

        if (docComment != null) {
            for (DocTree tag : docComment.getBlockTags()) {
                if (tag.getKind() == DocTree.Kind.PARAM) {
                    ParamTree paramTag = ((ParamTree) tag);
                    String name = paramTag.getName().getName().toString();
                    String desc = paramTag.getDescription().stream().map(DocTree::toString).collect(Collectors.joining(" ")).trim();

                    tags.put(name, desc);
                }
            }
        }

        JsonArray array = new JsonArray();

        for (VariableElement argumentDoc : methodDoc.getParameters()) {
            JsonObject argument = new JsonObject();

            String name = argumentDoc.getSimpleName().toString();

            argument.addProperty("name", name);
            argument.addProperty("type", argumentDoc.asType().toString());

            String comment = tags.get(name);

            if (comment != null && !comment.isEmpty())
                argument.addProperty("doc", comment);

            array.add(argument);
        }

        return array;
    }

    private static JsonElement compileReturn(ExecutableElement methodDoc, DocCommentTree docComment) {
        JsonObject returnObject = new JsonObject();

        returnObject.addProperty("type", methodDoc.getReturnType().toString());

        if (docComment != null) {
            List<ReturnTree> returnTags = docComment.getBlockTags().stream()
                    .filter(tag -> tag.getKind() == DocTree.Kind.RETURN)
                    .map(ReturnTree.class::cast)
                    .toList();

            if (!returnTags.isEmpty()) {
                ReturnTree returnTag = returnTags.get(0);
                String doc = returnTag.getDescription().stream().map(DocTree::toString).collect(Collectors.joining(" "));

                returnObject.addProperty("doc", doc);
            }
        }

        return returnObject;
    }

    private static void write(JsonObject object) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("./docs.json"));

            writer.write(JsonDoclet.jsonToPretty(object));
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String jsonToPretty(JsonObject object) {
        StringWriter writer = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(writer);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        jsonWriter.setIndent(JsonDoclet.spaceDelimiter);
        gson.toJson(object, jsonWriter);

        return writer.toString();
    }
}