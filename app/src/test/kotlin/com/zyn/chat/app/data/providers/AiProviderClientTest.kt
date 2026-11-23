package com.zyn.chat.app.data.providers

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A provider's `/models` payload is the only place a model's abilities come from, so what the app
 * reads out of each shape a provider uses is pinned here. Nothing is ever read out of a model's name.
 */
class AiProviderClientTest {

    @Test
    fun `OpenRouter's architecture lists the modalities`() {
        val model = model("""{"id":"one","architecture":{"input_modalities":["text","image"]}}""")

        assertTrue(model.acceptsImages)
    }

    @Test
    fun `a modality summary is read as inputs before the arrow`() {
        val vision = model("""{"id":"one","architecture":{"modality":"text+image->text"}}""")
        val textOnly = model("""{"id":"two","architecture":{"modality":"text->text"}}""")

        assertTrue(vision.acceptsImages)
        assertEquals(false, textOnly.capabilities.imageSupport)
    }

    @Test
    fun `top level input modalities are read too`() {
        val model = model("""{"id":"one","input_modalities":["text","image"],"max_input_tokens":200000}""")

        assertTrue(model.acceptsImages)
        assertEquals(200_000, model.contextWindowTokens)
    }

    @Test
    fun `a capabilities object answers both ways`() {
        val yes = model("""{"id":"one","capabilities":{"image_input":{"supported":true}}}""")
        val no = model("""{"id":"two","capabilities":{"image_input":{"supported":false}}}""")

        assertTrue(yes.acceptsImages)
        assertEquals(false, no.capabilities.imageSupport)
    }

    @Test
    fun `supported parameters say whether tools work`() {
        val model = model("""{"id":"one","supported_parameters":["temperature","tools"]}""")

        assertEquals(true, model.capabilities.supportsTools)
    }

    @Test
    fun `a reported context window is taken from either field providers use`() {
        val openRouter = model("""{"id":"one","context_length":128000}""")
        val other = model("""{"id":"two","max_input_tokens":200000}""")

        assertEquals(128_000, openRouter.contextWindowTokens)
        assertEquals(200_000, other.contextWindowTokens)
    }

    @Test
    fun `a provider that says nothing leaves the abilities unknown`() {
        // The name is all this payload has, and it must not be read as an ability.
        val model = model("""{"id":"gpt-4o","name":"GPT-4o","object":"model","owned_by":"system"}""")

        assertNull(model.capabilities.imageSupport)
        assertNull(model.capabilities.supportsTools)
    }

    @Test
    fun `a model whose name looks text-only is not blocked either`() {
        val model = model("""{"id":"o1-mini"}""")

        assertNull(model.capabilities.imageSupport)
    }

    @Test
    fun `an unreadable payload is reported as a failure`() {
        val result = runBlocking {
            AiProviderClient(httpClient = clientReturning("not json"), moshi = MOSHI)
                .listModels(PROVIDER, "test-key")
        }

        assertTrue(result.isFailure)
    }

    /** The one model of a payload that lists exactly [entry]. */
    private fun model(entry: String): AiModel = models("""{"data":[$entry]}""").single()

    private fun models(body: String): List<AiModel> = runBlocking {
        AiProviderClient(httpClient = clientReturning(body), moshi = MOSHI)
            .listModels(PROVIDER, "test-key")
            .getOrThrow()
    }
}

/** Answers every request with [body], so no provider is ever called. */
private fun clientReturning(body: String): OkHttpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
    .build()

private val MOSHI = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

private val PROVIDER = AiProvider(
    id = "test",
    name = "Test",
    endpoint = ProviderEndpoint(baseUrl = "https://example.com/v1", auth = ProviderAuth.Bearer),
)
