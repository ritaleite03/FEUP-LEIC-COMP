package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;

import static pt.up.fe.comp2024.ast.Kind.TYPE;

public class OptUtils {
    private static int tempNumber = -1;

    public static String getTemp() {

        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {

        return prefix + getNextTempNum();
    }

    public static int getNextTempNum() {

        tempNumber += 1;
        return tempNumber;
    }

    public static String toOllirType(JmmNode typeNode) {

        if (!typeNode.getKind().startsWith("Type")) {
            TYPE.checkOrThrow(typeNode);
        }

        String typeName = typeNode.get("name");

        return toOllirType(new Type(typeName, NodeUtils.getBooleanAttribute(typeNode, "isArray", "false")));
    }

    public static String toOllirType(Type type) {
        if (type == null)
            return null;
        if (type.isArray()) {
            return ".array" + toOllirType(type.getName());
        }
        return toOllirType(type.getName());
    }

    public static String toOllirType(String typeName) {

        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "String" -> "String";
            case "void" -> "V";
            default -> typeName;
        };

        return type;
    }

}
