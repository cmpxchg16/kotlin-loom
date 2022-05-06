package kotlinx.loom.server

import kotlinx.loom.client.Stats
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.StandardSocketOptions
import java.util.concurrent.atomic.LongAdder
import org.apache.commons.cli.*;


class Stats internal constructor() {
    val connections: LongAdder
    val requests: LongAdder
    val errors: LongAdder

    init {
        connections = LongAdder()
        requests = LongAdder()
        errors = LongAdder()
    }
}

class DummyHttpServer internal constructor() {
    private val DUMMY_HTTP_RESPONSE = "HTTP/1.1 200 OK\r\n" +
                                      "Connection: close\r\n" +
                                      "Content-Type: text/plain\r\n" +
                                      "Access-Control-Allow-Origin: *\r\n" +
                                      "Content-Length: 2\r\n" +
                                      "\r\n" +
                                      "42"

    private val DUMMY_HTTP_RESPONSE_BYTES = DUMMY_HTTP_RESPONSE.toByteArray()

    fun server(host: String, port: Int, backlog: Int, stats: Stats, debug: Boolean) {
        Thread.startVirtualThread {
            ServerSocket(port, backlog, InetAddress.getByName(host)).use { s ->
                s.setOption<Boolean>(StandardSocketOptions.SO_REUSEADDR, true)
                s.setOption<Boolean>(StandardSocketOptions.SO_REUSEPORT, true)
                while (true) {
                    val socket: Socket = s.accept()
                    stats.connections.increment()
                    Thread.startVirtualThread {
                        try {
                            socket.use { s ->
                                val buffer = ByteArray(128)
                                val input: InputStream = s.getInputStream()
                                val output: OutputStream = s.getOutputStream()
                                while (true) {
                                    val bytes = input.read(buffer)
                                    if (bytes < 0) {
                                        break
                                    }
                                    output.write(DUMMY_HTTP_RESPONSE_BYTES)
                                    stats.requests.increment()
                                }
                            }
                        } catch (e: Exception) {
                            if (debug) {
                                e.printStackTrace()
                                println("host: ${host}, port: ${port}")
                            }
                            stats.errors.increment()
                        } finally {
                            stats.connections.decrement()
                        }
                    }
                }
            }
        }
    }

    companion object {
        @JvmStatic fun main(args : Array<String>) {
            val options = Options()
            val hostArg = Option("h", "host", true, "Host (default 0.0.0.0)")
            options.addOption(hostArg)
            val portArg = Option("p", "port", true, "Base Port (default 8080)")
            options.addOption(portArg)
            val numOfPortsArg = Option("n", "num-ports", true, "Number Of Ports (default 10)")
            options.addOption(numOfPortsArg)
            val backLogArg = Option("b", "backlog", true, "Server requested maximum length of the queue of incoming connections (default 16192)")
            options.addOption(backLogArg)
            val debugArg = Option("d", "debug", true, "Debug (show stacktrace)")
            options.addOption(debugArg)

            var host = "0.0.0.0"
            var port = 8080
            var numOfPorts = 10
            var backlog = 16192
            var debug = false

            val cmd: CommandLine
            val parser: CommandLineParser = DefaultParser()
            val helper = HelpFormatter()

            try {
                cmd = parser.parse(options, args)
                if (cmd.hasOption("h")) {
                    host = cmd.getOptionValue("h");
                }
                if (cmd.hasOption("p")) {
                    port = cmd.getOptionValue("p").toInt();
                }
                if (cmd.hasOption("b")) {
                    backlog = cmd.getOptionValue("b").toInt();
                }
                if (cmd.hasOption("d")) {
                    debug = true
                }
                if (cmd.hasOption("n")) {
                    numOfPorts = cmd.getOptionValue("n").toInt();
                }
            } catch (e: ParseException) {
                helper.printHelp("Kotlin Loom Server", options)
                System.exit(0)
            }

            println("Host: ${host}, Port: ${port}, Backlog: ${backlog}, Ports: ${numOfPorts}, Debug: ${debug}\n")

            val stats = Stats()

            Thread.startVirtualThread {
                while (true) {
                    try {
                        println("Stats: #Connections: ${stats.connections.sum()}, #Requests: ${stats.requests.sum()}, #Errors: ${stats.errors.sum()}")
                        Thread.sleep(500)
                    } catch (e: Exception) {
                        if (debug) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            for (i in 0..numOfPorts) {
                DummyHttpServer().server(host, port + i, backlog, stats, debug)
            }

            Thread.sleep(Long.MAX_VALUE);
        }
    }
}