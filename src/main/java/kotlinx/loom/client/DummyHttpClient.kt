package kotlinx.loom.client

import java.net.InetSocketAddress
import java.net.Socket
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.LongAdder
import org.apache.commons.cli.*;
import java.net.StandardSocketOptions

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

class DummyHttpClient internal constructor() {
    private val DUMMY_HTTP_REQUEST =
            "GET HTTP/1.1\r\n" +
            "Host: 42\r\n" +
            "\r\n"

    private val DUMMY_HTTP_REQUEST_BYTES = DUMMY_HTTP_REQUEST.toByteArray()

    fun client(host: String, port: Int, timeout: Int, stats: Stats, debug: Boolean) {
        Thread.startVirtualThread {
            while (true) {
                try {
                    Socket().use { s ->
                        s.setOption<Boolean>(StandardSocketOptions.SO_REUSEADDR, true)
                        s.setOption<Boolean>(StandardSocketOptions.SO_REUSEPORT, true)
                        s.setSoTimeout(timeout)
                        s.connect(InetSocketAddress(host, port), timeout)
                        stats.connections.increment()
                        val input: InputStream = s.getInputStream()
                        val output: OutputStream = s.getOutputStream()
                        while (true) {
                            val buffer = ByteArray(26)
                            output.write(DUMMY_HTTP_REQUEST_BYTES)
                            val bytes = input.read(buffer)
                            if (bytes < 0) {
                                break
                            }
                            stats.requests.increment()
                        }
                    }
                } catch (e: Exception) {
                    if (debug) {
                        e.printStackTrace()
                        println("host: ${host}, port: ${port}")
                    }
                    stats.errors.increment()
                }
            }
        }
    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            val options = Options()
            val hostArg = Option("h", "host", true, "Host (default 0.0.0.0)")
            options.addOption(hostArg)
            val portArg = Option("p", "port", true, "Base Port (default 8080)")
            options.addOption(portArg)
            val numOfPortsArg = Option("n", "num-ports", true, "Number Of Ports (default 10)")
            options.addOption(numOfPortsArg)
            val timeoutArg = Option("t", "timeout", true, "So Timeout (default 65536)")
            options.addOption(timeoutArg)
            val clientsArg = Option("c", "clients", true, "Number of concurrent clients (default 1024)")
            options.addOption(clientsArg)
            val debugArg = Option("d", "debug", true, "Debug (show stacktrace)")
            options.addOption(debugArg)

            var host = "0.0.0.0"
            var port = 8080
            var numOfPorts = 10
            var clients = 1024
            var timeout = 65536
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
                if (cmd.hasOption("c")) {
                    clients = cmd.getOptionValue("c").toInt();
                }
                if (cmd.hasOption("t")) {
                    timeout = cmd.getOptionValue("t").toInt();
                }
                if (cmd.hasOption("d")) {
                    debug = true
                }
                if (cmd.hasOption("n")) {
                    numOfPorts = cmd.getOptionValue("n").toInt();
                }
            } catch (e: ParseException) {
                helper.printHelp("Kotlin Loom Client", options)
                System.exit(0)
            }

            println("Host: ${host}, Port: ${port}, Clients: ${clients} Timeout: ${timeout}, Ports: ${numOfPorts}, Debug: ${debug}\n")

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
                for (c in 1..clients) {
                    DummyHttpClient().client(host, port + i, timeout, stats, debug)
                }
            }

            Thread.sleep(Long.MAX_VALUE);
        }
    }
}