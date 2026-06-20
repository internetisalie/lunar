package net.internetisalie.lunar.run.test

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestIgnoredEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent
import com.intellij.openapi.util.Key

class LuaTestOutputToEventsConverter(
    testFrameworkName: String,
    private val consoleProperties: TestConsoleProperties
) : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {

    private val myBuffer = StringBuilder()
    private var nextId = 1

    // Lunity states
    private val lunitySuiteStack = mutableListOf<String>()
    private val lunityTestNameToId = mutableMapOf<String, String>()
    private val lunitySuiteNameToId = mutableMapOf<String, String>()

    private val config: LuaTestRunConfiguration?
        get() = (consoleProperties as? LuaTestConsoleProperties)?.configuration

    override fun processConsistentText(text: String, outputType: Key<*>) {
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
            // Busted
            myBuffer.append(text)
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

    private fun handleLunitySuiteStart(name: String, locationUrl: String?) {
        val id = "suite_${nextId++}"
        val parentId = lunitySuiteStack.lastOrNull()
        lunitySuiteStack.add(id)
        lunitySuiteNameToId[name] = id
        val startedEvent = TestSuiteStartedEvent(name, id, parentId, locationUrl, null, null, null, true)
        processor?.onSuiteStarted(startedEvent)
    }

    private fun handleLunitySuiteEnd(name: String) {
        val id = lunitySuiteNameToId[name] ?: (if (lunitySuiteStack.isNotEmpty()) lunitySuiteStack.removeLast() else "suite_unknown")
        if (lunitySuiteStack.isNotEmpty() && lunitySuiteStack.last() == id) {
            lunitySuiteStack.removeLast()
        }
        val finishedEvent = TestSuiteFinishedEvent(name, id)
        processor?.onSuiteFinished(finishedEvent)
    }

    private fun handleLunityTestStart(name: String, locationUrl: String?) {
        val id = "test_${nextId++}"
        val parentId = lunitySuiteStack.lastOrNull()
        lunityTestNameToId[name] = id
        val startedEvent = TestStartedEvent(name, id, parentId, locationUrl, null, null, null, true)
        processor?.onTestStarted(startedEvent)
    }

    private fun handleLunityTestPass(name: String, duration: Long) {
        val id = lunityTestNameToId[name]
        val finishedEvent = TestFinishedEvent(name, id, duration)
        processor?.onTestFinished(finishedEvent)
        lunityTestNameToId.remove(name)
    }

    private fun handleLunityTestFailure(name: String, isError: Boolean, duration: Long, message: String?, trace: String?) {
        val id = lunityTestNameToId[name]
        val failedEvent = TestFailedEvent(
            name, id, message ?: "Test failed", trace, isError,
            null, null, null, null, false, false, duration
        )
        processor?.onTestFailure(failedEvent)
        val finishedEvent = TestFinishedEvent(name, id, duration)
        processor?.onTestFinished(finishedEvent)
        lunityTestNameToId.remove(name)
    }

    private fun handleLunityTestIgnore(name: String, locationUrl: String?, message: String?, trace: String?) {
        val id = lunityTestNameToId[name] ?: "test_${nextId++}"
        val parentId = lunitySuiteStack.lastOrNull()
        if (!lunityTestNameToId.containsKey(name)) {
            val startedEvent = TestStartedEvent(name, id, parentId, locationUrl, null, null, null, true)
            processor?.onTestStarted(startedEvent)
        }
        val ignoredEvent = TestIgnoredEvent(name, id, message ?: "Ignored", trace)
        processor?.onTestIgnored(ignoredEvent)
        lunityTestNameToId.remove(name)
    }

    private fun processBustedOutput() {
        val fullText = myBuffer.toString()
        val jsonMatch = findTopLevelJson(fullText)
        if (jsonMatch != null) {
            val (jsonStr, range) = jsonMatch
            val before = fullText.substring(0, range.first)
            val after = fullText.substring(range.last + 1)
            if (before.isNotEmpty()) {
                fireOnUncapturedOutput(before, ProcessOutputTypes.STDOUT)
            }
            try {
                val element = JsonParser.parseString(jsonStr)
                if (element.isJsonObject) {
                    processBustedJson(element.asJsonObject)
                } else {
                    fireOnUncapturedOutput(jsonStr, ProcessOutputTypes.STDOUT)
                }
            } catch (e: Exception) {
                fireOnUncapturedOutput(jsonStr, ProcessOutputTypes.STDOUT)
            }
            if (after.isNotEmpty()) {
                fireOnUncapturedOutput(after, ProcessOutputTypes.STDOUT)
            }
        } else {
            if (fullText.isNotEmpty()) {
                fireOnUncapturedOutput(fullText, ProcessOutputTypes.STDOUT)
            }
        }
    }

    private fun processBustedJson(obj: JsonObject) {
        val startedSuites = mutableMapOf<String, String>()
        val suitesToClose = mutableListOf<Pair<String, String>>()

        fun getOrCreateSuites(parts: List<String>): String? {
            var currentPath = ""
            var parentId: String? = null
            for (i in 0 until parts.size - 1) {
                val suiteName = parts[i]
                currentPath = if (currentPath.isEmpty()) suiteName else "$currentPath \u2192 $suiteName"
                var id = startedSuites[currentPath]
                if (id == null) {
                    id = "suite_${nextId++}"
                    startedSuites[currentPath] = id
                    suitesToClose.add(suiteName to id)
                    val startedEvent = TestSuiteStartedEvent(suiteName, id, parentId, null, null, null, null, true)
                    processor?.onSuiteStarted(startedEvent)
                }
                parentId = id
            }
            return parentId
        }

        fun processBustedItem(item: JsonObject, status: String) {
            val fullName = item.get("name")?.asString ?: ""
            val parts = fullName.split(" \u2192 ")
            val testName = parts.last()
            val parentId = getOrCreateSuites(parts)

            val traceObj = item.get("trace")?.asJsonObject
            val source = traceObj?.get("source")?.asString
            val currentline = traceObj?.get("currentline")?.asLong
            val durationSec = item.get("duration")?.asDouble ?: 0.0
            val durationMs = (durationSec * 1000).toLong()

            val locationUrl = if (source != null && currentline != null) {
                val cleanSource = source.removePrefix("@")
                "lua://$cleanSource:$currentline"
            } else null

            val testId = "test_${nextId++}"

            handleBustedItemStatus(status, testName, testId, parentId, locationUrl, durationMs, item, traceObj)
        }

        obj.get("successes")?.asJsonArray?.forEach {
            if (it.isJsonObject) processBustedItem(it.asJsonObject, "success")
        }
        obj.get("failures")?.asJsonArray?.forEach {
            if (it.isJsonObject) processBustedItem(it.asJsonObject, "failure")
        }
        obj.get("errors")?.asJsonArray?.forEach {
            if (it.isJsonObject) processBustedItem(it.asJsonObject, "error")
        }
        obj.get("pendings")?.asJsonArray?.forEach {
            if (it.isJsonObject) processBustedItem(it.asJsonObject, "pending")
        }

        for (i in suitesToClose.indices.reversed()) {
            val (name, id) = suitesToClose[i]
            val finishedEvent = TestSuiteFinishedEvent(name, id)
            processor?.onSuiteFinished(finishedEvent)
        }
    }

    private fun handleBustedItemStatus(
        status: String,
        testName: String,
        testId: String,
        parentId: String?,
        locationUrl: String?,
        durationMs: Long,
        item: JsonObject,
        traceObj: JsonObject?
    ) {
        when (status) {
            "success" -> {
                val startedEvent = TestStartedEvent(testName, testId, parentId, locationUrl, null, null, null, true)
                processor?.onTestStarted(startedEvent)
                val finishedEvent = TestFinishedEvent(testName, testId, durationMs)
                processor?.onTestFinished(finishedEvent)
            }
            "failure", "error" -> {
                val message = item.get("message")?.asString ?: "Test failed"
                val trace = traceObj?.get("trace")?.asString ?: ""
                val startedEvent = TestStartedEvent(testName, testId, parentId, locationUrl, null, null, null, true)
                processor?.onTestStarted(startedEvent)
                val failedEvent = TestFailedEvent(
                    testName, testId, message, trace, status == "error",
                    null, null, null, null, false, false, durationMs
                )
                processor?.onTestFailure(failedEvent)
                val finishedEvent = TestFinishedEvent(testName, testId, durationMs)
                processor?.onTestFinished(finishedEvent)
            }
            "pending" -> {
                val startedEvent = TestStartedEvent(testName, testId, parentId, locationUrl, null, null, null, true)
                processor?.onTestStarted(startedEvent)
                val ignoredEvent = TestIgnoredEvent(testName, testId, "Pending", null)
                processor?.onTestIgnored(ignoredEvent)
            }
        }
    }

    private fun findTopLevelJson(text: String): Pair<String, IntRange>? {
        var openBraces = 0
        var startIndex = -1
        var inString = false
        var stringChar = ' '
        var escape = false

        for (i in text.indices) {
            val char = text[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (char == '\\') {
                    escape = true
                } else if (char == stringChar) {
                    inString = false
                }
            } else {
                if (char == '"' || char == '\'') {
                    inString = true
                    stringChar = char
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
}
