package io.ktor.samples.fullstack.frontend

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.samples.fullstack.common.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import org.w3c.dom.Image
import org.w3c.dom.get

private val client = HttpClient(Js)
private val scope = MainScope()
private val endpoint = window.location.origin

@Suppress("unused")
@JsName("helloWorld")
fun helloWorld(salutation: String) {
    console.log("RETRIEVED")
    println("TEST!@#!@!#")
    val message = "$salutation from Kotlin.JS ${getCommonWorldString()}"
    document.getElementById("js-response")?.textContent = message


    scope.launch {
        delay(3000)
        document.getElementById("js-response")?.textContent = "making request..."
        delay(3000)
        val response = client.get<String> { url.takeFrom(endpoint); url.pathComponents("test") }

        document.getElementById("js-response")?.textContent = """result is: "$response""""
    }
}

@JsName("requestCode")
fun requestCode() {
    scope.launch {
        val response = client.get<String> { url.takeFrom(endpoint); url.pathComponents("challenge") }
        val split = response.split(":")
        val qr = split.last()
        (document.getElementById("qr-code") as Image).src = "data:image/png;base64, $qr"

        while (true) {
            delay(500)
            val response = client.get<String> {
                url.takeFrom(endpoint); url.pathComponents("validate"); url.parameters.append(
                "challenge",
                split.first()
            )
            }
            if (response != "") {
                document.getElementsByClassName("wrap-login100")[0]!!.innerHTML =
                        "<img src =\"https://www.pngitem.com/pimgs/m/35-350426_profile-icon-png-default-profile-picture-png-transparent.png\" style = \"display: inline;margin-left: auto;margin-right: auto;width: 50%;\">" +
                "<p style=\"font-size: 25px; text-align: center;\">Good day, $response.</p>"
                val style = document.createElement(".wrap-login100 { flex-direction: column } ")
                document.getElementsByClassName("wrap-login100")[0]!!.appendChild(style)
            }
        }
    }
}

fun main() {
    document.addEventListener("DOMContentLoaded", {
        requestCode()
    })
}