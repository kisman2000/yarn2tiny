import java.io.File
import java.lang.IllegalArgumentException
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

    fun mapDescriptor(
        descriptor : String
    ) = if(descriptor.startsWith('L') || (descriptor.startsWith('[') && descriptor.contains("[L"))) {
        val arrayDimension = if(descriptor.contains("[")) {
            descriptor.substring(0..descriptor.lastIndexOf('['))
        } else {
            ""
        }

        val className = descriptor.removePrefix("${arrayDimension}L").removeSuffix(";")
        val classEntry = findEntry(classes, className) { it.official }

        "${arrayDimension}L${classEntry?.intermediary ?: className};"
    } else {
        descriptor
    }

    fun mapMethodType(
        type : String
    ) = try {
        val signatureNode = SignatureNode(type)
        val params = mutableListOf<String>()
        val returnType = mapDescriptor(signatureNode.returnType)

        for(param in signatureNode.params) {
            params.add(mapDescriptor(param))
        }

        "(${params.joinToString("")})$returnType"
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
                        val type = split[2]

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

    for(field in fields) {
        if(field.type.contains("L")) {
            field.type = mapDescriptor(field.type)
        }
    }

    for(method in methods) {
        if(method.type.contains("L")) {
            method.type = mapMethodType(method.type)
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

class SignatureNode(
    signature : String
) : SignatureVisitor() {
    private var listeningParams = false
    private var listeningReturnType = false
    private var arrayDimension = 0

    val params = mutableListOf<String>()
    var returnType = "V"

    init {
        SignatureReader(signature).accept(this)
    }

    override fun visitParamType() = this.also {
        listeningParams = true
        listeningReturnType = false
    }

    override fun visitReturnType() = this.also {
        listeningParams = false
        listeningReturnType = true
    }

    override fun visitArrayType() = this.also {
        arrayDimension++
    }

    override fun visitBaseType(
        descriptor : Char
    ) {
        if(listeningParams) {
            params.add("${"[".repeat(arrayDimension)}$descriptor")
            arrayDimension = 0
        } else if(listeningReturnType) {
            returnType = "${"[".repeat(arrayDimension)}$descriptor"

            listeningReturnType = false
            arrayDimension = 0
        }
    }

    override fun visitClassType(
        name : String
    ) {
        if(listeningParams) {
            params.add("${"[".repeat(arrayDimension)}L$name;")
            arrayDimension = 0
        } else if(listeningReturnType) {
            returnType = "${"[".repeat(arrayDimension)}L$name;"

            listeningReturnType = false
            arrayDimension = 0
        }
    }

    override fun visitInnerClassType(
        name : String
    ) {
        visitClassType(name)
    }

    override fun visitEnd() {
        listeningParams = false
        listeningReturnType = false
        arrayDimension = 0
    }
}

//Only MethodTypeSignature without generic types
abstract class SignatureVisitor {
    open fun visitParamType() = this
    open fun visitReturnType() = this
    open fun visitArrayType() = this

    open fun visitBaseType(
        descriptor : Char
    ) { }

    open fun visitClassType(
        name : String
    ) { }

    open fun visitInnerClassType(
        name : String
    ) { }

    open fun visitEnd() { }
}

//Only MethodTypeSignature without generic types
class SignatureReader(
    private val signature : String
) {
    fun accept(
        visitor : SignatureVisitor
    ) {
        var offset = 1

        while(signature[offset] != ')') {
           offset = parseType(signature, offset, visitor.visitParamType())
        }

        parseType(signature, offset + 1, visitor.visitReturnType())
    }

    private fun parseType(
        signature : String,
        offset : Int,
        visitor : SignatureVisitor
    ) : Int {
        var startOffset = offset
        var char = signature[startOffset++]

        return when(char) {
            'Z', 'C', 'B', 'S', 'I', 'F', 'J', 'D', 'V' -> {
                visitor.visitBaseType(char)

                startOffset
            }

            '[' -> {
                parseType(signature, startOffset, visitor.visitArrayType())
            }

            'L' -> {
                val start = startOffset
                val name : String?

                while(true) {
                    char = signature[startOffset++]

                    if(char == ';') {
                        name = signature.substring(start, startOffset - 1)

                        visitor.visitClassType(name)
                        visitor.visitEnd()

                        break
                    }
                }

                startOffset
            }

            else -> {
                throw IllegalArgumentException()
            }
        }
    }
}