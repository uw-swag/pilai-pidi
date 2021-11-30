package ca.uwaterloo.swag.pilaipidi.util;

public class TypeChecker {

    public static boolean isAssignable(String expectedType, String actualType) {
        if (expectedType == null) {
            return false;
        }
        if (actualType == null) {
            return false;
        }

        if ("null".equals(actualType)) {
            return true;
        }

        return expectedType.equals(actualType);
    }
}
