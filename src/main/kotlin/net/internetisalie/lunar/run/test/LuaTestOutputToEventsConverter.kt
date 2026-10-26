package net.internetisalie.lunar.run.test

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestIgnoredEvent
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent
import com.intellij.execution.testframework.sm.runner.events.TreeNodeEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key

class LuaTestOutputToEventsConverter(
    testFrameworkName: String,
    private val consoleProperties: TestConsoleProperties,
) : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {
    private val myBuffer = StringBuilder()
    private var nextId = 1

    // Lunity states
    private val lunitySuiteStack = mutableListOf<String>()
    private val lunityTestNameToId = mutableMapOf<String, String>()
    private val lunitySuiteNameToId = mutableMapOf<String, String>()

    private val config: LuaTestRunConfiguration?
        get() = (consoleProperties as? LuaTestConsoleProperties)?.configuration

    override fun processConsistentText(
        text: String,
        outputType: Key<*>,
    ) {
        val framework = config?.testFramework ?: LuaTestFramework.BUSTED
        if (framework == LuaTestFramework.LUNITY) {
            val trimmed = text.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    val element = JsonParser.parseString(trimmed)
                    if (element.isJsonObject) {
                        processLunityEvent(element.asJsonObject)
                        return
                    }
                } catch (e: Exception) {
                    // fallback to raw console
                }
            }
            fireOnUncapturedOutput(text, outputType)
        } else {
            // Busted: buffer for the terminal JSON report, and forward live so output is
            // visible during the run instead of only at process termination (§2.5.7).
            myBuffer.append(text)
            fireOnUncapturedOutput(text, outputType)
        }
    }

    override fun flushBufferOnProcessTermination(exitCode: Int) {
        super.flushBufferOnProcessTermination(exitCode)
        val framework = config?.testFramework ?: LuaTestFramework.BUSTED
        if (framework == LuaTestFramework.BUSTED) {
            processBustedOutput()
        }
    }

    private fun processLunityEvent(obj: JsonObject) {
        val event = obj.get("event")?.asString ?: return
        val name = obj.get("name")?.asString ?: ""
        val file = obj.get("file")?.asString
        val line = obj.get("line")?.asLong
        val duration = obj.get("duration")?.asLong ?: 0L
        val message = obj.get("message")?.asString
        val trace = obj.get("trace")?.asString

        val locationUrl = if (file != null && line != null) "lua://$file:$line" else null

        when (event) {
            "suite_start" -> handleLunitySuiteStart(name, locationUrl)
            "suite_end" -> handleLunitySuiteEnd(name)
            "test_start" -> handleLunityTestStart(name, locationUrl)
            "test_pass" -> handleLunityTestPass(name, duration)
            "test_fail", "test_error" -> handleLunityTestFailure(name, event == "test_error", duration, message, trace)
            "test_ignore" -> handleLunityTestIgnore(name, locationUrl, message, trace)
        }
    }

    private fun handleLunitySuiteStart(
        name: String,
        locationUrl: String?,
    ) {
        val id = "suite_${nextId++}"
        val parentId = lunitySuiteStack.lastOrNull() ?: TreeNodeEvent.ROOT_NODE_ID
        lunitySuiteStack.add(id)
        lunitySuiteNameToId[name] = id
        val startedEvent = TestSuiteStartedEvent(name, id, parentId, locationUrl, null, null, null, true)
        processor?.onSuiteStarted(startedEvent)
    }

    private fun handleLunitySuiteEnd(name: String) {
        val id =
            lunitySuiteNameToId[name]
                ?: (if (lunitySuiteStack.isNotEmpty()) lunitySuiteStack.removeLast() else "suite_unknown")
        if (lunitySuiteStack.isNotEmpty() && lunitySuiteStack.last() == id) {
            lunitySuiteStack.removeLast()
        }
        val finishedEvent = TestSuiteFinishedEvent(name, id)
        processor?.onSuiteFinished(finishedEvent)
    }

    private fun handleLunityTestStart(
        name: String,
        locationUrl: String?,
    ) {
        val id = "test_${nextId++}"
        val parentId = lunitySuiteStack.lastOrNull() ?: TreeNodeEvent.ROOT_NODE_ID
        lunityTestNameToId[name] = id
        val startedEvent = TestStartedEvent(name, id, parentId, locationUrl, null, null, null, true)
        processor?.onTestStarted(startedEvent)
    }

    private fun handleLunityTestPass(
        name: String,
        duration: Long,
    ) {
        val id = lunityTestNameToId[name]
        val finishedEvent = TestFinishedEvent(name, id, duration)
        processor?.onTestFinished(finishedEvent)
        lunityTestNameToId.remove(name)
    }

    private fun handleLunityTestFailure(
        name: String,
        isError: Boolean,
        duration: Long,
        message: String?,
        trace: String?,
    ) {
        val id = lunityTestNameToId[name]
        val msg = message ?: "Test failed"
        val diff = parseAssertionDiff(msg)
        val failedEvent =
            TestFailedEvent(
                name,
                id,
                msg,
                trace,
                isError,
                diff?.second,
                diff?.first,
                null,
                null,
                false,
                false,
                duration,
            )
        processor?.onTestFailure(failedEvent)
        val finishedEvent = TestFinishedEvent(name, id, duration)
        processor?.onTestFinished(finishedEvent)
        lunityTestNameToId.remove(name)
    }

    private fun handleLunityTestIgnore(
        name: String,
        locationUrl: String?,
        message: String?,
        trace: String?,
    ) {
        val id = lunityTestNameToId[name] ?: "test_${nextId++}"
        val parentId = lunitySuiteStack.lastOrNull() ?: TreeNodeEvent.ROOT_NODE_ID
        if (!lunityTestNameToId.containsKey(name)) {
            val startedEvent = TestStartedEvent(name, id, parentId, locationUrl, null, null, null, true)
            processor?.onTestStarted(startedEvent)
        }
        val ignoredEvent = TestIgnoredEvent(name, id, message ?: "Ignored", trace)
        processor?.onTestIgnored(ignoredEvent)
        lunityTestNameToId.remove(name)
    }

    private fun processBustedOutput() {
        // Raw stdout/stderr has already streamed live via processConsistentText (§2.5.7), so this
        // terminal pass only builds the structured test tree from the JSON report — re-forwarding
        // the buffered text here would duplicate every line in the console.
        val fullText = myBuffer.toString()
        val jsonMatch = findTopLevelJson(fullText) ?: return
        val (jsonStr, _) = jsonMatch
        try {
            val element = JsonParser.parseString(jsonStr)
            if (element.isJsonObject) {
                processBustedJson(element.asJsonObject)
            }
        } catch (e: Exception) {
            LOG.warn("Failed to parse busted JSON report", e)
        }
    }

    private fun processBustedJson(obj: JsonObject) {
        val suites = BustedSuites()
        for ((section, status) in BUSTED_SECTIONS) {
            val items = obj.get(section)?.takeIf { it.isJsonArray }?.asJsonArray ?: continue
            items.filter { it.isJsonObject }.forEach {
                val entry = LuaBustedReportEntry.parse(it.asJsonObject)
                emitBustedTest(entry, status, suites.parentIdFor(entry))
            }
        }
        suites.finishAll()
    }

    /**
     * Opens one suite per spec file and, inside it, one per distinct describe path, so every test
     * node gets a parent the platform's id-based tree knows; a test with no known file hangs off
     * the tree root ([TreeNodeEvent.ROOT_NODE_ID]).
     */
    private inner class BustedSuites {
        private val idsByKey = mutableMapOf<List<String?>, String>()
        private val opened = mutableListOf<Pair<String, String>>()

        fun parentIdFor(entry: LuaBustedReportEntry): String {
            val fileId = entry.file?.let { open(listOf(it), TreeNodeEvent.ROOT_NODE_ID, "lua://$it:1") }
            val parentOfDescribe = fileId ?: TreeNodeEvent.ROOT_NODE_ID
            return entry.describePath?.let { open(listOf(entry.file, it), parentOfDescribe, null) } ?: parentOfDescribe
        }

        fun finishAll() {
            opened.asReversed().forEach { (name, id) -> processor?.onSuiteFinished(TestSuiteFinishedEvent(name, id)) }
        }

        private fun open(
            key: List<String?>,
            parentId: String,
            locationUrl: String?,
        ): String =
            idsByKey.getOrPut(key) {
                val name = key.last().orEmpty()
                val id = "suite_${nextId++}"
                opened.add(name to id)
                processor?.onSuiteStarted(
                    TestSuiteStartedEvent(name, id, parentId, locationUrl, null, null, null, true),
                )
                id
            }
    }

    private fun emitBustedTest(
        entry: LuaBustedReportEntry,
        status: String,
        parentId: String,
    ) {
        val testName = entry.testName
        val testId = "test_${nextId++}"
        processor?.onTestStarted(
            TestStartedEvent(testName, testId, parentId, entry.locationUrl, null, null, null, true),
        )
        when (status) {
            "failure", "error" -> {
                val message = entry.message ?: "Test failed"
                val diff = parseAssertionDiff(message)
                val failedEvent =
                    TestFailedEvent(
                        testName,
                        testId,
                        message,
                        entry.traceback.orEmpty(),
                        status == "error",
                        diff?.second,
                        diff?.first,
                        null,
                        null,
                        false,
                        false,
                        entry.durationMs,
                    )
                processor?.onTestFailure(failedEvent)
            }
            "pending" -> processor?.onTestIgnored(TestIgnoredEvent(testName, testId, "Pending", null))
        }
        if (status != "pending") processor?.onTestFinished(TestFinishedEvent(testName, testId, entry.durationMs))
    }

    private fun findTopLevelJson(text: String): Pair<String, IntRange>? {
        var openBraces = 0
        var startIndex = -1
        var inString = false
        var escape = false

        for (i in text.indices) {
            val char = text[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (char == '\\') {
                    escape = true
                } else if (char == '"') {
                    inString = false
                }
            } else {
                if (char == '"') {
                    inString = true
                } else if (char == '{') {
                    if (openBraces == 0) {
                        startIndex = i
                    }
                    openBraces++
                } else if (char == '}') {
                    if (openBraces > 0) {
                        openBraces--
                        if (openBraces == 0 && startIndex != -1) {
                            return text.substring(startIndex, i + 1) to (startIndex..i)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun parseAssertionDiff(message: String): Pair<String, String>? {
        val bustedEqualMatch = BUSTED_EQUAL_PATTERN.find(message)
        if (bustedEqualMatch != null) {
            val actual = bustedEqualMatch.groupValues[1].trim()
            val expected = bustedEqualMatch.groupValues[2].trim()
            if (expected.isNotEmpty() && actual.isNotEmpty()) {
                return Pair(expected, actual)
            }
        }

        val expectedFirstMatch = EXPECTED_FIRST_PATTERN.find(message)
        if (expectedFirstMatch != null) {
            val expected = expectedFirstMatch.groupValues[1].trim()
            val actual = expectedFirstMatch.groupValues[2].trim()
            if (expected.isNotEmpty() && actual.isNotEmpty()) {
                return Pair(expected, actual)
            }
        }

        val actualFirstMatch = ACTUAL_FIRST_PATTERN.find(message)
        if (actualFirstMatch != null) {
            val actual = actualFirstMatch.groupValues[1].trim()
            val expected = actualFirstMatch.groupValues[2].trim()
            if (expected.isNotEmpty() && actual.isNotEmpty()) {
                return Pair(expected, actual)
            }
        }

        return null
    }

    companion object {
        private val BUSTED_EQUAL_PATTERN =
            Regex(
                "Expected objects to be equal\\.\\s*\\n*Passed in:\\s*\\n*(.*?)\\s*\\n+Expected:\\s*\\n*(.*)",
                kotlin.text.RegexOption.DOT_MATCHES_ALL,
            )

        private val EXPECTED_FIRST_PATTERN =
            Regex(
                "Expected:?\\s*\\n?(.*?)\\n\\s*(?:Passed in|Got|Actual):?\\s*\\n?(.*)",
                kotlin.text.RegexOption.DOT_MATCHES_ALL,
            )

        private val ACTUAL_FIRST_PATTERN =
            Regex(
                "(?:Passed in|Got|Actual):?\\s*\\n?(.*?)\\n\\s*Expected:?\\s*\\n?(.*)",
                kotlin.text.RegexOption.DOT_MATCHES_ALL,
            )

        private val BUSTED_SECTIONS =
            listOf("successes" to "success", "failures" to "failure", "errors" to "error", "pendings" to "pending")

        private val LOG = Logger.getInstance(LuaTestOutputToEventsConverter::class.java)
    }
}
