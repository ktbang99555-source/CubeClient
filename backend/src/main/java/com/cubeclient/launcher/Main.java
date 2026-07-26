package com.cubeclient.launcher;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length == 0) {
            System.out.println("{\"type\":\"error\",\"message\":\"no subcommand given\"}");
            return 1;
        }
        if ("ping".equals(args[0])) {
            System.out.println("{\"type\":\"pong\"}");
            return 0;
        }
        System.out.println("{\"type\":\"error\",\"message\":\"unknown subcommand: " + args[0] + "\"}");
        return 1;
    }
}
