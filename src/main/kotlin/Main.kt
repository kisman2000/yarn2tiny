import java.io.File
import java.lang.IndexOutOfBoundsException
import kotlin.system.exitProcess

fun main(
    args : Array<String>
) {
    val timestamp = System.currentTimeMillis()
    var warnings = 0

    fun exit(
        reason : String
    ) {
        println("$reason\nUsage: \"yarn mappings file\" \"tiny mappings file\"")

        exitProcess(0)
    }

    fun warning(
        message : String
    ) {
        println("Warning! $message")

        warnings++
    }

    if(args.size != 2) {
        exit("Not enough arguments!")
    }

    val input = File(args[0])
    val output = File(args[1])

    if(!input.exists()) {
        exit("Yarn mappings file does not exist!")
    }

    if(input.isDirectory) {
        exit("Yarn mappings file is directory!")
    }

    if(output.exists()) {
        println("Tiny mappings file will be overwritten!")

        output.delete()
    }

    output.createNewFile()

    val classes = mutableListOf<YarnEntry>()
    val fields = mutableListOf<YarnEntry>()
    val methods = mutableListOf<YarnEntry>()

    fun findEntry(
        entries : List<YarnEntry>,
        name : String,
        getter : (YarnEntry) -> String
    ) : YarnEntry? {
        for(entry in entries) {
            if(name == getter(entry)) {
                return entry
            }
        }

        return null
    }

    fun mapType(
        type : String
    ) = try {
        val returnType = type.split(")")[1]
        val paramTypes = type.removePrefix("(").removeSuffix(")$returnType").split(";")
        var mappedReturnType = ""
        val mappedParamTypes = mutableListOf<String>()

        if(returnType.contains("L")) {
            var className = returnType.removePrefix("L").removeSuffix(";")
            val classEntry = findEntry(classes, className) { it.official }

            if(classEntry != null) {
                className = classEntry.intermediary
            }

            mappedReturnType = "L$className;"
        }

        for(paramType in paramTypes) {
            if(paramType.isNotEmpty() && paramType.startsWith("L")) {
                var className = paramType.removePrefix("L")
                val classEntry = findEntry(classes, className) { it.official }

                if(classEntry != null) {
                    className = classEntry.intermediary
                }

                mappedParamTypes.add("L$className")
            } else {
                mappedParamTypes.add(paramType)
            }
        }

        "(${mappedParamTypes.joinToString(";")})$mappedReturnType"
    } catch(exception : IndexOutOfBoundsException) {
        exit("Cannot map $type description!")

        throw exception
    }

    println()
    println("Parsing yarn mappings!")

    for((index, line) in input.readLines().withIndex()) {
        if(index == 0) {
            if(!line.startsWith("v1")) {
                exit("Only v1 mappings are supported!")
            }

            continue
        }

        val split = line.split("\t")
        val entryType = split[0]

        if(split.size >= 2) {
            when (entryType) {
                "CLASS" -> {
                    if(split.size < 4) {
                        warning("Whitespace count mismatch, got ${split.size} while expected at least 4 [$line]")
                    } else {
                        val official = split[1]
                        val intermediary = split[2]
                        val named = split[3]

                        classes.add(YarnEntry(official, intermediary, named))
                    }
                }

                "FIELD" -> {
                    if(split.size < 6) {
                        warning("Whitespace count mismatch, got ${split.size} while expected at least 6 [$line]")
                    } else {
                        var official = split[1]
                        val intermediary = split[4]
                        val named = split[5]
                        var type = split[2]

                        val classEntry = findEntry(classes, official) { it.official }

                        if(classEntry != null) {
                            official = classEntry.intermediary
                        }

                        fields.add(YarnEntry(official, intermediary, named, type))
                    }
                }

                "METHOD" -> {
                    if(split.size < 6) {
                        warning("Whitespace count mismatch, got ${split.size} while expected at least 6 [$line]")
                    } else {
                        var official = split[1]
                        val intermediary = split[4]
                        val named = split[5]
                        val type = split[2]

                        val classEntry = findEntry(classes, official) { it.official }

                        if(classEntry != null) {
                            official = classEntry.intermediary
                        }

                        methods.add(YarnEntry(official, intermediary, named, type))
                    }
                }

                else -> warning("Unknown mapping type ${split[0]}")
            }
        }
    }

    for(method in methods) {
        if(method.type.contains("L")) {
            method.type = mapType(method.type)
        }
    }

    println("Parsed ${classes.size + fields.size + methods.size} lines of yarn mappings!")
    println()
    println("Writing tiny mappings!")

    val writer = output.writer()

    writer.write("v1\t")
    writer.appendLine()

    println("Parsing ${classes.size} class entries!")

    for(entry in classes) {
        writer.write("CLASS\t${entry.intermediary}\t${entry.named}")
        writer.appendLine()
    }

    println("Parsing ${fields.size} field entries!")

    for(entry in fields) {
        writer.write("FIELD\t${entry.official}\t${entry.type}\t${entry.intermediary}\t${entry.named}")
        writer.appendLine()
    }

    println("Parsing ${methods.size} method entries!")

    for(entry in methods) {
        writer.write("METHOD\t${entry.official}\t${entry.type}\t${entry.intermediary}\t${entry.named}")
        writer.appendLine()
    }

    writer.close()

    println("Written ${classes.size + fields.size + methods.size} lines of tiny mappings!")
    println()
    println("Everything took ${System.currentTimeMillis() - timestamp} ms and got $warnings warnings!")
}

open class YarnEntry(
    val official : String,
    val intermediary : String,
    val named : String,
    var type : String = ""
)