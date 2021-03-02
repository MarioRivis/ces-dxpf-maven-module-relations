import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import java.io.File
import java.nio.file.Files
import java.util.stream.Collectors
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.inputStream

@ExperimentalPathApi
fun main(args: Array<String>) {

    if (args.size != 2) {
        print("Please provide both arguments")
        return
    }

    val outputFilePath = args[0]
    val baseFolderPath = args[1]

    val pomFiles = Files.walk(File(baseFolderPath).toPath())
            .filter { Files.isRegularFile(it) }
            .filter { it.endsWith("pom.xml") }
            .collect(Collectors.toList())

    val mavenReader = MavenXpp3Reader()

    val modules = pomFiles
            .mapNotNull { pomFile ->
                val mavenModel = mavenReader.read(pomFile.inputStream())
                val mavenModuleId = MavenModuleId(
                        mavenModel.groupId ?: mavenModel.parent.groupId,
                        mavenModel.artifactId,
                        mavenModel.version ?: mavenModel.parent.version)
                val deps = mavenModel.dependencies.mapNotNull { MavenModuleId(it.groupId, it.artifactId, it.version) }
                MavenModule(mavenModuleId, deps)
            }
            .mapNotNull { Pair(it.id, it) }
            .toMap()

    val nodes = modules.keys.map { GraphNode(it.artifactID) }
    val links = modules.values
            .map { module ->
                module.dependencies
                        .mapNotNull { modules[it] }
                        .map { GraphLink(module.id.artifactID, it.id.artifactID) }
            }
            .flatten()

    val graph = Graph(nodes, links)

    val result = Result(
            name = "CES - Maven Module Relations",
            description = "Shows relations between maven modules",
            entity = "MODULES",
            visualTags = listOf("digraph", "hierarchical-edge-bundle", "forced-layered-graph"),
            content = graph,
            timestamp = System.currentTimeMillis()
    )

    jacksonObjectMapper().writerWithDefaultPrettyPrinter().writeValue(File(outputFilePath), listOf(result))

}

data class MavenModule(
        val id: MavenModuleId,
        var dependencies: List<MavenModuleId> = ArrayList()
)

class MavenModuleId(
        val groupID: String?,
        val artifactID: String,
        val version: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MavenModuleId

        if (groupID != other.groupID) return false
        if (artifactID != other.artifactID) return false

        return true
    }

    override fun hashCode(): Int {
        var result = groupID?.hashCode() ?: 0
        result = 31 * result + artifactID.hashCode()
        return result
    }
}

data class Graph(
        val nodes: List<GraphNode>,
        val links: List<GraphLink>
)

data class GraphNode(
        val name: String,
        val component: Number = 1
)

data class GraphLink(
        val source: String,
        val target: String,
        val value: Number = 1
)

data class Result(
        val name: String,
        val description: String,
        val visualTags: List<String>,
        val entity: String,
        val timestamp: Long,
        val content: Graph
)
