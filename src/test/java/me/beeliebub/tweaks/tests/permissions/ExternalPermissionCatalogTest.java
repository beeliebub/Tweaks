package me.beeliebub.tweaks.tests.permissions;

import me.beeliebub.tweaks.permissions.ExternalPermissionCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalPermissionCatalogTest {

    @TempDir
    Path directory;

    @Test
    void parsesCommentsHeadersDescriptionsLowercaseAndFirstDuplicate() throws Exception {
        Files.writeString(directory.resolve("my-plugin.txt"), """
                # name: Initial Name
                # a comment

                Essentials.TP | Teleport yourself
                ESSENTIALS.TP | Later description
                essentials.gamemode
                """, StandardCharsets.UTF_8);

        List<ExternalPermissionCatalog.ExternalCategory> categories = catalog().scan();

        assertEquals(1, categories.size());
        ExternalPermissionCatalog.ExternalCategory category = categories.getFirst();
        assertEquals("external:my-plugin", category.key());
        assertEquals("Initial Name", category.displayName());
        assertEquals(List.of(
                new ExternalPermissionCatalog.ExternalNode("essentials.tp", "Teleport yourself"),
                new ExternalPermissionCatalog.ExternalNode("essentials.gamemode", "essentials.gamemode")),
                category.nodes());
    }

    @Test
    void lastNameHeaderWinsAndFilenameDefaultsToTitleCase() throws Exception {
        Files.writeString(directory.resolve("my_plugin.txt"), "# name: First\n# name: Second\nnode.one\n");
        Files.writeString(directory.resolve("other-addon.TXT"), "node.two\n");

        List<ExternalPermissionCatalog.ExternalCategory> categories = catalog().scan();

        assertEquals("Second", categories.get(0).displayName());
        assertEquals("Other Addon", categories.get(1).displayName());
    }

    @Test
    void missingDirectoryAndSubdirectoriesProduceNoCategories() throws Exception {
        Path missing = directory.resolve("missing");
        assertTrue(catalog(missing).scan().isEmpty());
        Files.createDirectory(directory.resolve("nested.txt"));
        assertTrue(catalog().scan().isEmpty());
    }

    @Test
    void invalidUtf8FileIsSkippedWithoutAffectingOtherFiles() throws Exception {
        Files.write(directory.resolve("bad.txt"), new byte[] {(byte) 0xc3, (byte) 0x28});
        Files.writeString(directory.resolve("good.txt"), "plugin.node | Works\n");

        List<ExternalPermissionCatalog.ExternalCategory> categories = catalog().scan();

        assertEquals(1, categories.size());
        assertEquals("external:good", categories.getFirst().key());
    }

    @Test
    void oversizedFilesAreSkipped() throws Exception {
        Files.write(directory.resolve("large.txt"),
                new byte[(int) ExternalPermissionCatalog.MAX_FILE_BYTES + 1]);

        assertTrue(catalog().scan().isEmpty());
    }

    @Test
    void lineCapKeepsOnlyTheFirstFiveThousandLines() throws Exception {
        StringBuilder contents = new StringBuilder();
        for (int i = 0; i <= ExternalPermissionCatalog.MAX_LINES; i++) {
            contents.append("plugin.node").append(i).append('\n');
        }
        Files.writeString(directory.resolve("many.txt"), contents.toString());

        List<ExternalPermissionCatalog.ExternalCategory> categories = catalog().scan();

        assertFalse(categories.isEmpty());
        assertEquals(ExternalPermissionCatalog.MAX_LINES, categories.getFirst().nodes().size());
        assertEquals("plugin.node0", categories.getFirst().nodes().getFirst().node());
    }

    @Test
    void toolsFilenameUsesAnExternalNamespace() throws Exception {
        Files.writeString(directory.resolve("tools.txt"), "other.admin\n");

        ExternalPermissionCatalog.ExternalCategory category = catalog().scan().getFirst();

        assertEquals("external:tools", category.key());
        assertTrue(category.key().startsWith("external:"));
    }

    private ExternalPermissionCatalog catalog() {
        return catalog(directory);
    }

    private ExternalPermissionCatalog catalog(Path path) {
        return new ExternalPermissionCatalog(path);
    }
}
