package com.zyn.chat.app.data.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A refusal to take a picture is the provider answering the ability question at send time, which is
 * the only honest source left for endpoints that report no abilities. What is learned from it, and
 * what it is allowed to override, is pinned here.
 */
class ImageAbilityTest {

    @Test
    fun `a complaint about pictures is read as a refusal`() {
        val refusal = failure(
            detail = """{"error":{"message":"Invalid content type. image_url is only supported by certain models."}}""",
        )

        assertTrue(refusal.refusedPictures())
    }

    @Test
    fun `a complaint about the size of a picture is not a refusal`() {
        val tooLarge = failure(detail = """{"error":{"message":"image is too large"}}""")

        assertFalse(tooLarge.refusedPictures())
    }

    @Test
    fun `a failure that does not mention pictures is not a refusal`() {
        val unauthorized = failure(
            detail = """{"error":{"message":"invalid api key"}}""",
            status = 401,
        )

        assertFalse(unauthorized.refusedPictures())
    }

    @Test
    fun `a failure with no response body is not a refusal`() {
        val silent = failure(detail = null)

        assertFalse(silent.refusedPictures())
    }

    @Test
    fun `a refused picture narrows a model whose provider stayed silent`() {
        val learned = ModelCatalog.enrich("test", "mystery-model").withRefusedPictures(refused = true)

        assertEquals(false, learned.capabilities.imageSupport)
    }

    @Test
    fun `a provider report beats a refused picture`() {
        val reported = ModelCatalog.enrich("test", "mystery-model")
            .copy(capabilities = ModelCapabilities.textAndImages())

        assertTrue(reported.withRefusedPictures(refused = true).acceptsImages)
    }

    @Test
    fun `a model with nothing remembered is left untouched`() {
        val model = ModelCatalog.enrich("test", "mystery-model")

        assertEquals(model, model.withRefusedPictures(refused = false))
    }

    /** A provider failure with a response body about pictures, unless told otherwise. */
    private fun failure(detail: String?, status: Int = 400): ProviderException = ProviderException(
        reason = ProviderError.Unknown,
        detail = detail,
        httpStatus = status,
    )
}
