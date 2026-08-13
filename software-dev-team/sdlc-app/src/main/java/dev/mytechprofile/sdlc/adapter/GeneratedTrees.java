package dev.mytechprofile.sdlc.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Directory names that are build output or VCS metadata, not source the SDLC team should review.
 */
final class GeneratedTrees {

    static final Set<String> DIRECTORY_NAMES =
            Set.of(".git", ".gradle", "build", "target", "out", "node_modules", "dist", "coverage");

    private GeneratedTrees() {}

    /**
     * Returns git pathspecs that include the repo minus generated trees.
     *
     * @return {@code .} plus {@code :(exclude)} entries
     */
    static List<String> sourcePathspecs() {
        List<String> specs = new ArrayList<>();
        specs.add(".");
        for (String name : DIRECTORY_NAMES) {
            if (".git".equals(name)) {
                continue;
            }
            specs.add(":(exclude)" + name);
            specs.add(":(exclude)" + name + "/**");
        }
        return List.copyOf(specs);
    }

    /**
     * Returns a {@code .gitignore} body for playground workspaces.
     *
     * @return ignore rules, one per line, ending with a newline
     */
    static String gitignore() {
        return String.join(
                        "\n",
                        ".gradle/",
                        "build/",
                        "target/",
                        "out/",
                        "node_modules/",
                        "dist/",
                        "coverage/",
                        "*.class",
                        ".DS_Store")
                + "\n";
    }
}
