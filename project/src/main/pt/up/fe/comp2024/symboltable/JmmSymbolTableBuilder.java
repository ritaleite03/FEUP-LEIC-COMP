package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {

    public static JmmSymbolTable build(JmmNode root) {

        List<String> importsList = new ArrayList<>();
        var classDecl = root.getChildren(Kind.CLASS_DECL).get(0);

        for (JmmNode child : root.getChildren(Kind.IMPORT)) {
            importsList.add(String.join(".",child.getObjectAsList("name", String.class)));
        }

        String className = classDecl.get("name");

        var methods = buildMethods(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);
        var fields = buildFields(classDecl);

        return new JmmSymbolTable(importsList, className, classDecl.getOptional("superr").orElse(null), methods,
                returnTypes, params, locals, fields);
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, Type> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(
                        method -> {
                            var typ = method.getChild(0);
                            map.put(method.get("name"),
                                    new Type(typ.get("name"), typ.getObject("isArray", Boolean.class)));
                        });

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"),
                        method.getChildren(PARAM).stream().map(
                                param -> {
                                    var typ = param.getChild(0);
                                    return new Symbol(
                                            new Type(typ.get("name"), typ.getObject("isArray", Boolean.class)),
                                            param.get("name"));
                                }).toList()));
        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), getLocalsList(method)));

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {

        return classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();
    }

    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        // TODO: Simple implementation that needs to be expanded

        var intType = new Type(TypeUtils.getIntTypeName(), false);

        return methodDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> {
                    var typ = varDecl.getChild(0);
                    return new Symbol(new Type(typ.get("name"), typ.getObject("isArray", Boolean.class)),
                            varDecl.get("name"));
                })
                        .toList();
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        return classDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> {
                    var typ = varDecl.getChild(0);
                    return new Symbol(new Type(typ.get("name"), typ.getObject("isArray", Boolean.class)),
                            varDecl.get("name"));
                })
                .toList();
    }

}
