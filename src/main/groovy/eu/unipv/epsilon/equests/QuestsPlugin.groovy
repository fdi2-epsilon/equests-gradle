package eu.unipv.epsilon.equests

import org.gradle.api.*
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import org.yaml.snakeyaml.Yaml

class QuestsPlugin implements Plugin<Project> {

    public static final String GROUP_NAME = 'Enigma level'
    public static final String QUEST_DESCRIPTION_FILENAME = 'quest-description'
    public static final String PACK_EXT = 'eqc'

    public static final String TEMP_PATH = 'tmp/eqcfiles'
    public static final String OUT_PATH = 'eqcs'

    void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class)

        // Add extension objects
        project.extensions.create('questCollection', CollectionMetaExt)

        // Add tasks
        Task genMetadata = project.task('genMetadata') {
            inputs.dir 'src/eqc'
            outputs.file "$project.buildDir/$TEMP_PATH/metadata.yaml"

            doLast {
                def file = project.file("$project.buildDir/$TEMP_PATH/metadata.yaml")
                file.parentFile.mkdirs()

                def fw = file.newWriter()
                new Yaml().dump(searchQuestsMeta(project), fw)
                fw.close()
            }
        }

        Task processSource = project.task(type: Copy, 'processSource') {
            from 'src/eqc'
            into "$project.buildDir/$TEMP_PATH"
            exclude "*/$QUEST_DESCRIPTION_FILENAME"
        } << { Copy task ->
            // Post copy, rename directories to standard format (i.e. 01, 02, ...)
            new File(task.destinationDir, 'quests').mkdir()
            task.destinationDir.eachDir {
                try {
                    def dirIdx = (it.name.takeWhile { it != '.' }).toInteger()
                    def dirName = String.format('%02d', dirIdx)
                    it.renameTo("$task.destinationDir/quests/$dirName")
                }
                catch (Exception e) {
                    if (it.name != 'quests') println("Unrecognized dir: $it.name")
                }
            }
        }

        Task pack = project.task(type: Zip, dependsOn: [processSource, genMetadata], 'pack') {
            baseName project.name
            extension PACK_EXT
            destinationDir project.file("$project.buildDir/$OUT_PATH")
            from processSource.destinationDir
            // exclude '**/*.txt'
        }

        [genMetadata, processSource, pack].each { it.group = GROUP_NAME }

        project.build.dependsOn pack
    }

    static Map<String, Object> searchQuestsMeta(Project p) {
        def root = p.file('src/eqc')

        def descFiles = p.fileTree(root).include("*/$QUEST_DESCRIPTION_FILENAME")
        def questDirs = descFiles.collect { it.parentFile }

        // '*/quest-description' excludes root by default, but we do this anyway...
        if (questDirs.contains(root))
            throw new RuntimeException("Found $QUEST_DESCRIPTION_FILENAME in root.")

        def questsInfo = questDirs.collect { File dir ->
            def match = (dir.name =~ /(\d+). (.+)/)
            if (!match.hasGroup() || match.size() != 1)
                throw new RuntimeException("Malformed directory name: $dir.name")

            def questDescription = p.file("$dir.path/$QUEST_DESCRIPTION_FILENAME").text.trim()
            [name: match[0][2], description: questDescription]
        }

        CollectionMetaExt props = p.questCollection
        return [name: props.name, description: props.description, quests: questsInfo]
    }

}

class CollectionMetaExt {
    def String name = 'Unnamed collection'
    def String description = ''
}
