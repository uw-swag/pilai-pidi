package ca.uwaterloo.swag.util;

public class TypeChecker {

    public static boolean isAssignable(String expectedType, String actualType) {
        if (expectedType == null) {
            return false;
        }
        if (actualType == null) {
            return false;
        }
        return expectedType.equals(actualType);
    }
}
