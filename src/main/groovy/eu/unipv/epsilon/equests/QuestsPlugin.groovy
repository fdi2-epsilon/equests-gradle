package eu.unipv.epsilon.equests

import org.gradle.api.*
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.bundling.Zip
import org.yaml.snakeyaml.Yaml

class QuestsPlugin implements Plugin<Project> {

    public static final String GROUP_NAME = 'Enigma level'
    public static final String QUEST_DESCRIPTION_FILENAME = 'quest-description'
    public static final String PACK_EXT = 'eqc'

    public static final String EQC_SOURCE_PATH = 'src/main/eqc'

    public static final String TEMP_PATH = 'eqcfiles'
    public static final String OUT_PATH = 'eqcs'

    void apply(Project project) {
        // BasePlugin has the clean task, but is already extended by JavaPlugin
        //project.getPluginManager().apply(BasePlugin.class)

        // Use JavaPlugin to compile templates, if any (generate 1.6 or 1.7 classes to be compatible with dex)
        project.getPluginManager().apply(JavaPlugin.class)
        project.sourceCompatibility = '1.7'
        project.targetCompatibility = '1.7'

        // Add extension objects
        project.extensions.create('questCollection', CollectionMetaExt)

        // Add tasks

        // Generates metadata
        Task genMetadata = project.task('genMetadata') {
            inputs.dir EQC_SOURCE_PATH
            inputs.file 'build.gradle'
            outputs.file "$project.buildDir/$TEMP_PATH/metadata.yaml"

            doLast {
                def file = project.file("$project.buildDir/$TEMP_PATH/metadata.yaml")
                file.parentFile.mkdirs()

                def fw = file.newWriter()
                new Yaml().dump(searchQuestsMeta(project), fw)
                fw.close()
            }
        }

        // Copies and renames data to output, excluding quest-description files
        Task processQuests = project.task(type: Copy, 'processQuests') {
            from EQC_SOURCE_PATH
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
                    if (it.name != 'quests' && it.name != 'classes')
                        println("Unrecognized dir: $it.name")
                }
            }
        }

        // Moves classes to output dir for packaging
        Task moveClasses = project.task(type: Copy, dependsOn: 'classes', 'moveClasses') {
            from project.sourceSets.main.output.classesDir,
                 project.sourceSets.main.output.resourcesDir
            into "$project.buildDir/$TEMP_PATH/classes"
        }

        // Compiles dex classes if dx is on the classpath
        Task dexClasses = project.task(dependsOn: moveClasses, 'dexClasses') {
            // Not using Exec task so we can catch failures

            // NOT WORKING!?
            //   inputs.dir   "$project.buildDir/$TEMP_PATH/classes"
            //   outputs.file "$project.buildDir/$TEMP_PATH/classes.dex"

            doLast {
                def proc = null;

                // Try UNIX/Linux
                try {
                    proc = Runtime.runtime.exec('dx --dex --output="classes.dex" "classes"',
                            null, new File("$project.buildDir/$TEMP_PATH"))
                } catch (IOException e1) {
                    // Try Windows
                    try {
                        proc = Runtime.runtime.exec('cmd /c dx --dex --output="classes.dex" "classes"',
                                null, new File("$project.buildDir/$TEMP_PATH"))
                    } catch (IOException e2) { }
                }

                if (proc != null) {
                    proc.waitFor()
                    if (proc.exitValue() == 0) return
                }

                println "warning: \"dx\" could not be found in the classpath, " +
                        "any template in this collection will not work on Android"
                throw new StopExecutionException();
            }
        }

        Task pack = project.task(type: Zip, dependsOn: [processQuests, moveClasses, dexClasses, genMetadata], 'pack') {
            baseName project.name
            extension PACK_EXT
            destinationDir project.file("$project.buildDir/$OUT_PATH")
            from processQuests.destinationDir
            // exclude '**/*.txt'
        }

        [genMetadata, processQuests, pack].each { it.group = GROUP_NAME }

        project.build.dependsOn pack
    }

    static Map<String, Object> searchQuestsMeta(Project p) {
        def root = p.file(EQC_SOURCE_PATH)

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
        def meta = [name: props.name]
        if (props.subtitle != null) meta.put('subtitle', props.subtitle)
        if (props.description != null) meta.put('description', props.description)
        meta.put('quests', questsInfo)

        return meta
    }

}

class CollectionMetaExt {
    def String name = 'Unnamed collection'
    def String subtitle
    def String description
}
