package io.ktor.samples.fullstack.backend

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver.Companion.IN_MEMORY
import io.ktor.application.*
import io.ktor.html.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.script
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.IPv8Configuration
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.AuthorityManager
import nl.tudelft.ipv8.attestation.communication.CommunicationManager
import nl.tudelft.ipv8.attestation.identity.database.IdentitySQLiteStore
import nl.tudelft.ipv8.attestation.revocation.AuthoritySQLiteStore
import nl.tudelft.ipv8.attestation.wallet.AttestationSQLiteStore
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint
import nl.tudelft.ipv8.sqldelight.Database
import nl.tudelft.ipv8.util.JavaEncodingUtils
import nl.tudelft.ipv8.util.toKey
import org.json.JSONObject
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.image.RenderedImage
import java.io.*
import java.net.InetAddress
import java.util.*
import javax.imageio.ImageIO

const val MY_PEER = "my_peer"
const val RENDEZVOUS_TOKEN = "123456789"
private lateinit var ipv8: IPv8
private lateinit var communicationManager: CommunicationManager

private val outputs = hashMapOf<String, String>()
private val outstanding = hashMapOf<String, ByteArray>()

fun initIPv8() {
    val myKey = JavaCryptoProvider.generateKey()
    val myPeer = Peer(myKey)
    val udpEndpoint = UdpEndpoint(4090, InetAddress.getByName("0.0.0.0"))
    val endpoint = EndpointAggregator(udpEndpoint, null)

    val config = IPv8Configuration(
        overlays = listOf(
        ), walkerInterval = 1.0
    )

    val driver: SqlDriver = JdbcSqliteDriver(IN_MEMORY)
    Database.Schema.create(driver)
    val database = Database(driver)
    val authorityStore = AuthoritySQLiteStore(database)
    val attestationStore = AttestationSQLiteStore(database)
    val identityStore = IdentitySQLiteStore(database)

    val authorityManager = AuthorityManager(authorityStore)

    ipv8 = IPv8(endpoint, config, myPeer)
    communicationManager = CommunicationManager(ipv8, attestationStore, identityStore, authorityManager)
    ipv8.start()
    communicationManager.load("my_peer")
}


fun imgToBase64String(img: RenderedImage?, formatName: String?): String? {
    val os = ByteArrayOutputStream()
    return try {
        ImageIO.write(img, formatName, os)
        Base64.getEncoder().encodeToString(os.toByteArray())
    } catch (ioe: IOException) {
        throw UncheckedIOException(ioe)
    }
}

private fun createQRImage(qrFile: File, qrCodeText: String, size: Int, fileType: String): String? {
    // Create the ByteMatrix for the QR-Code that encodes the given String
    val hintMap: Hashtable<EncodeHintType, ErrorCorrectionLevel> = Hashtable()
    hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L)
    val qrCodeWriter = QRCodeWriter()
    val byteMatrix: BitMatrix = qrCodeWriter.encode(qrCodeText, BarcodeFormat.QR_CODE, size, size, hintMap)
    // Make the BufferedImage that are to hold the QRCode
    val matrixWidth: Int = byteMatrix.width
    val image = BufferedImage(matrixWidth, matrixWidth, BufferedImage.TYPE_INT_RGB)
    image.createGraphics()
    val graphics = image.graphics as Graphics2D
    graphics.color = Color.WHITE
    graphics.fillRect(0, 0, matrixWidth, matrixWidth)
    // Paint and save the image using the ByteMatrix
    graphics.color = Color.BLACK
    for (i in 0 until matrixWidth) {
        for (j in 0 until matrixWidth) {
            if (byteMatrix.get(i, j)) {
                graphics.fillRect(i, j, 1, 1)
            }
        }
    }
    return imgToBase64String(image, fileType)
//    ImageIO.write(image, fileType, qrFile)
}

fun Application.main() {
    val currentDir = File(".").absoluteFile
    environment.log.info("Current directory: $currentDir")

    val html = File("src/backendMain/resources/index.html").readText()

    GlobalScope.launch {
        while (true) {
            val channel = communicationManager.load(MY_PEER)
            channel.verificationOutput
        }
    }

    routing {
        get("/test") {
            call.respond("I am a test response")
        }

        get("/challenge") {
            val channel = communicationManager.load(MY_PEER)
            val challenge = channel.generateDisclosureRequest("NAME")
            val myPublicKey = channel.myPeer.publicKey
            val qrData = JSONObject()
            qrData.put("presentation", "presentation_request")
            qrData.put("public_key", JavaEncodingUtils.encodeBase64ToString(myPublicKey.keyToBin()))
//            qrData.put("rendezvous", RENDEZVOUS_TOKEN)
            qrData.put("name", "NAME")
            qrData.put("id", challenge)
            val qr = createQRImage(File("src/backendMain/resources/images/img-qr.png"), qrData.toString(), 500, "png")
            val responseData = "$challenge:$qr"

            call.respondText(responseData, ContentType.Text.Plain, HttpStatusCode.OK)
        }

        get("/validate") {
//            call.respondHtml {
//                body {
//                    +"Hello ${getCommonWorldString()} from Ktor"
//                    div {
//                        id = "js-response"
//                        +"Loading..."
//                    }
//                    script(src = "/static/output.js") {
//                    }
//
//                }
//            }
            val channel = communicationManager.load(MY_PEER)
//            val hash = outstanding[call.parameters["challenge"]]

//            if (hash != null) {
//                val result = channel.verificationOutput[hash.toKey()]
//                if (result != null) {
            var name: String? = null

            if (channel.verificationOutput.size > 0 &&
                channel.verificationOutput.values.toList()[0].isNotEmpty() &&
                channel.verificationOutput.values.toList()[0].filter { it.second != null && it.second!! > 0.9 }
                    .isNotEmpty()
            ) {
                name =
                    String(channel.verificationOutput.values.toList()[0].filter { it.second != null && it.second!! > 0.9 }[0].first)
            }
//                }
//        }
            if (name != null) {
                call.respondText(name, ContentType.Text.Plain, HttpStatusCode.OK)
            } else {
                call.respondText("", ContentType.Text.Plain, HttpStatusCode.OK)
            }
        }

        get("/") {
            call.respondText(html, ContentType.Text.Html)
        }

        static("/") {
            resources()
            default("/static/index.html")
        }

//        static("/css") {
//            resources()
//        }
//        static("/fonts") {
//            resources()
//        }
//        static("/fonts") {
//            resources()
//        }
    }
}

fun main(args: Array<String>) {
    initIPv8()
    embeddedServer(Netty, port = 8080) { main() }.start(wait = true)
}