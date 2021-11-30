package ca.uwaterloo.swag.pilaipidi;

/**
 * Entry point for CLI based PilaiPidi invocation.
 *
 * @since 0.0.1
 */
public class Main {

    public static void main(String[] args) {
        PilaiPidi pilaiPidi = new PilaiPidi();
        pilaiPidi.invoke(args);
    }
}
