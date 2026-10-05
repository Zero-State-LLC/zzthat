package com.zer0state.zzthat

import com.zer0state.zzthat.api.ApiResult
import com.zer0state.zzthat.api.DemoApi
import com.zer0state.zzthat.app.BuildKind
import com.zer0state.zzthat.grammar.CheckResult
import com.zer0state.zzthat.grammar.CheckWord
import com.zer0state.zzthat.grammar.Grammar
import com.zer0state.zzthat.grammar.WordListLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Which bundled list the app reads with (Danny, #43 A2), and the demo codes that list checks (#43 A4). */
class WordlistChoiceTest {
    private val lists = WordListLoader.load(File(requireNotNull(System.getProperty("zz.wordlists"))))

    @Test
    fun beforeDiscoveryTheAppClassifiesAgainstProtoV0(): Unit = runBlocking {
        val app = TestApp.make(TestApp.demoService(), kind = BuildKind.DEMO, bundledLists = lists)
        assertEquals("proto-v0", app.pipeline.classifyList?.version)
        assertNull("no local check word without the server's version (FR-044)", app.pipeline.checkList)
        app.refreshDiscovery()
        assertEquals(DemoApi.DISCOVERY.wordlistVersion, app.pipeline.classifyList?.version)
        assertEquals(DemoApi.DISCOVERY.wordlistVersion, app.pipeline.checkList?.version)
    }

    /** #43 A4: every demo word code, owned or minted, passes the check of the list the demo discovery names. */
    @Test
    fun theDemoWordCodesPassTheirListsCheck(): Unit = runBlocking {
        val list = lists.single { it.version == DemoApi.DISCOVERY.wordlistVersion }
        val service = TestApp.demoService()
        val owned = (service.myCodes() as ApiResult.Ok).value.codes.filter { it.checkWord != null }.map { it.canonical }
        val minted = DemoApi.MINT_SEQUENCE.map { it.first }
        assertTrue("zz-river-falcon-sky-zz" in owned)
        for (canonical in owned + minted) {
            val code = Grammar.parse(canonical).codeOrNull() ?: error("$canonical does not parse")
            assertEquals(canonical, CheckResult.Ok, CheckWord.verify(code, list))
        }
    }
}
