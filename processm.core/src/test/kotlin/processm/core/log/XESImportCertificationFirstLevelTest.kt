package processm.core.log

import io.mockk.every
import io.mockk.spyk
import processm.core.log.attribute.value
import java.text.SimpleDateFormat
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Certification Level A1, B1, C1, D1, X1 based on http://www.xes-standard.org/certification
 */
internal class XESImportCertificationFirstLevelTest {
    @Test
    fun `Level A1 = XES Event data that only contains case identifiers and activity names`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <extension name="Concept" prefix="concept" uri="http://www.xes-standard.org/concept.xesext"/>
                <extension name="Identity" prefix="identity" uri="http://www.xes-standard.org/identity.xesext"/>
                <string key="concept:name" value="Log concept:name"/>
                <string key="identity:id" value="Log identity:id"/>
                <trace>
                    <string key="concept:name" value="Trace #001"/>
                    <string key="identity:id" value="T-001"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #001"/>
                        <string key="identity:id" value="E-001"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #001"/>
                        <string key="identity:id" value="E-002"/>
                    </event>
                </trace>
                <trace>
                    <string key="concept:name" value="Trace #002"/>
                    <string key="identity:id" value="T-002"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #002"/>
                        <string key="identity:id" value="E-003"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #002"/>
                        <string key="identity:id" value="E-004"/>
                    </event>
                </trace>
            </log>
        """.trimIndent().byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()
            val receivedLog: Log = iterator.next() as Log

            with(receivedLog.extensions) {
                assertEquals(size, 2)

                assertEquals(getValue("concept").name, "Concept")
                assertEquals(getValue("concept").prefix, "concept")

                assertEquals(getValue("identity").name, "Identity")
                assertEquals(getValue("identity").prefix, "identity")
            }

            with(receivedLog) {
                assertEquals(conceptName, "Log concept:name")
                assertEquals(identityId, "Log identity:id")
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #001")
                assertEquals(identityId, "T-001")
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #1 in Trace #001")
                assertEquals(identityId, "E-001")
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #2 in Trace #001")
                assertEquals(identityId, "E-002")
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #002")
                assertEquals(identityId, "T-002")
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #1 in Trace #002")
                assertEquals(identityId, "E-003")
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #2 in Trace #002")
                assertEquals(identityId, "E-004")
            }
        }
    }

    @Test
    fun `Level A1 = XES Event data that only contains case identifiers and activity names also when not used standard extensions`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <string key="concept:name" value="Log concept:name"/>
                <string key="identity:id" value="Log identity:id"/>
                <trace>
                    <string key="concept:name" value="Trace #001"/>
                    <string key="identity:id" value="T-001"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #001"/>
                        <string key="identity:id" value="E-001"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #001"/>
                        <string key="identity:id" value="E-002"/>
                    </event>
                </trace>
                <trace>
                    <string key="concept:name" value="Trace #002"/>
                    <string key="identity:id" value="T-002"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #002"/>
                        <string key="identity:id" value="E-003"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #002"/>
                        <string key="identity:id" value="E-004"/>
                    </event>
                </trace>
            </log>
        """.trimIndent().byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()
            val receivedLog: Log = iterator.next() as Log

            with(receivedLog.extensions) {
                assertEquals(size, 0)
            }

            with(receivedLog) {
                assertEquals(conceptName, "Log concept:name")
                assertEquals(identityId, "Log identity:id")
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #001")
                assertEquals(identityId, "T-001")
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #1 in Trace #001")
                assertEquals(identityId, "E-001")
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #2 in Trace #001")
                assertEquals(identityId, "E-002")
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #002")
                assertEquals(identityId, "T-002")
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #1 in Trace #002")
                assertEquals(identityId, "E-003")
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #2 in Trace #002")
                assertEquals(identityId, "E-004")
            }
        }
    }

    @Test
    fun `Level B1 = Level A1 extended with event types (lifecycleTransition attribute) and timestamps (timeTimestamp attribute)`() {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SX")
        dateFormatter.timeZone = TimeZone.getTimeZone("UTC")
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <extension name="Concept" prefix="concept" uri="http://www.xes-standard.org/concept.xesext"/>
                <extension name="Identity" prefix="identity" uri="http://www.xes-standard.org/identity.xesext"/>
                <extension name="Lifecycle" prefix="lifecycle" uri="http://www.xes-standard.org/lifecycle.xesext"/>
                <extension name="Time" prefix="time" uri="http://www.xes-standard.org/time.xesext"/>
                <string key="concept:name" value="Log concept:name"/>
                <string key="identity:id" value="Log identity:id"/>
                <string key="lifecycle:model" value="standard"/>
                <trace>
                    <string key="concept:name" value="Trace #001"/>
                    <string key="identity:id" value="T-001"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #001"/>
                        <string key="identity:id" value="E-001"/>
                        <string key="lifecycle:transition" value="start"/>
                        <date key="time:timestamp" value="2005-01-01T00:00:00.000+01:00"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #001"/>
                        <string key="identity:id" value="E-002"/>
                        <string key="lifecycle:transition" value="complete"/>
                        <date key="time:timestamp" value="2005-01-03T00:00:00.000+01:00"/>
                    </event>
                </trace>
                <trace>
                    <string key="concept:name" value="Trace #002"/>
                    <string key="identity:id" value="T-002"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #002"/>
                        <string key="identity:id" value="E-003"/>
                        <string key="lifecycle:transition" value="schedule"/>
                        <date key="time:timestamp" value="2005-01-04T00:00:00.000+01:00"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #002"/>
                        <string key="identity:id" value="E-004"/>
                        <string key="lifecycle:transition" value="complete"/>
                        <date key="time:timestamp" value="2005-01-05T00:00:00.000+01:00"/>
                    </event>
                </trace>
            </log>
        """.trimIndent().byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()
            val receivedLog: Log = iterator.next() as Log

            with(receivedLog.extensions) {
                assertEquals(size, 4)

                assertEquals(getValue("lifecycle").name, "Lifecycle")
                assertEquals(getValue("lifecycle").prefix, "lifecycle")

                assertEquals(getValue("time").name, "Time")
                assertEquals(getValue("time").prefix, "time")
            }

            with(receivedLog) {
                assertEquals(lifecycleModel, "standard")
            }

            assert(iterator.next() is Trace)

            with(iterator.next() as Event) {
                assertEquals(lifecycleTransition, "start")
                assertEquals(timeTimestamp, dateFormatter.parse("2005-01-01T00:00:00.000+01:00"))
            }

            with(iterator.next() as Event) {
                assertEquals(lifecycleTransition, "complete")
                assertEquals(timeTimestamp, dateFormatter.parse("2005-01-03T00:00:00.000+01:00"))
            }

            assert(iterator.next() is Trace)

            with(iterator.next() as Event) {
                assertEquals(lifecycleTransition, "schedule")
                assertEquals(timeTimestamp, dateFormatter.parse("2005-01-04T00:00:00.000+01:00"))
            }

            with(iterator.next() as Event) {
                assertEquals(lifecycleTransition, "complete")
                assertEquals(timeTimestamp, dateFormatter.parse("2005-01-05T00:00:00.000+01:00"))
            }
        }
    }

    @Test
    fun `Level C1 = Level B1 extended with information on resources (orgResource attribute)`() {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SX")
        dateFormatter.timeZone = TimeZone.getTimeZone("UTC")
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <extension name="Concept" prefix="concept" uri="http://www.xes-standard.org/concept.xesext"/>
                <extension name="Identity" prefix="identity" uri="http://www.xes-standard.org/identity.xesext"/>
                <extension name="Lifecycle" prefix="lifecycle" uri="http://www.xes-standard.org/lifecycle.xesext"/>
                <extension name="Time" prefix="time" uri="http://www.xes-standard.org/time.xesext"/>
                <extension name="Organizational" prefix="org" uri="http://www.xes-standard.org/org.xesext"/>
                <string key="concept:name" value="Log concept:name"/>
                <string key="identity:id" value="Log identity:id"/>
                <string key="lifecycle:model" value="standard"/>
                <trace>
                    <string key="concept:name" value="Trace #001"/>
                    <string key="identity:id" value="T-001"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #001"/>
                        <string key="identity:id" value="E-001"/>
                        <string key="lifecycle:transition" value="start"/>
                        <date key="time:timestamp" value="2005-01-01T00:00:00.000+01:00"/>
                        <string key="org:group" value="Endoscopy"/>
                        <string key="org:resource" value="Drugs"/>
                        <string key="org:role" value="Intern"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #001"/>
                        <string key="identity:id" value="E-002"/>
                        <string key="lifecycle:transition" value="complete"/>
                        <date key="time:timestamp" value="2005-01-03T00:00:00.000+01:00"/>
                        <string key="org:group" value="Endoscopy"/>
                        <string key="org:resource" value="Pills"/>
                        <string key="org:role" value="Assistant"/>
                    </event>
                </trace>
                <trace>
                    <string key="concept:name" value="Trace #002"/>
                    <string key="identity:id" value="T-002"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #002"/>
                        <string key="identity:id" value="E-003"/>
                        <string key="lifecycle:transition" value="schedule"/>
                        <date key="time:timestamp" value="2005-01-04T00:00:00.000+01:00"/>
                        <string key="org:group" value="Radiotherapy"/>
                        <string key="org:resource" value="Pills"/>
                        <string key="org:role" value="Intern"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #002"/>
                        <string key="identity:id" value="E-004"/>
                        <string key="lifecycle:transition" value="complete"/>
                        <date key="time:timestamp" value="2005-01-05T00:00:00.000+01:00"/>
                        <string key="org:group" value="Radiotherapy"/>
                        <string key="org:resource" value="Drugs"/>
                        <string key="org:role" value="Assistant"/>
                    </event>
                </trace>
            </log>
        """.trimIndent().byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()
            val receivedLog: Log = iterator.next() as Log

            with(receivedLog.extensions) {
                assertEquals(size, 5)

                assertEquals(getValue("org").name, "Organizational")
                assertEquals(getValue("org").prefix, "org")
            }

            assert(iterator.next() is Trace)

            with(iterator.next() as Event) {
                assertEquals(orgGroup, "Endoscopy")
                assertEquals(orgResource, "Drugs")
                assertEquals(orgRole, "Intern")
            }

            with(iterator.next() as Event) {
                assertEquals(orgGroup, "Endoscopy")
                assertEquals(orgResource, "Pills")
                assertEquals(orgRole, "Assistant")
            }

            assert(iterator.next() is Trace)

            with(iterator.next() as Event) {
                assertEquals(orgGroup, "Radiotherapy")
                assertEquals(orgResource, "Pills")
                assertEquals(orgRole, "Intern")
            }

            with(iterator.next() as Event) {
                assertEquals(orgGroup, "Radiotherapy")
                assertEquals(orgResource, "Drugs")
                assertEquals(orgRole, "Assistant")
            }
        }
    }

    @Test
    fun `Level D1 = Level C1 extended with attributes from any standard XES extension`() {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SX")
        dateFormatter.timeZone = TimeZone.getTimeZone("UTC")
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <extension name="Concept" prefix="concept" uri="http://www.xes-standard.org/concept.xesext"/>
                <extension name="Identity" prefix="identity" uri="http://www.xes-standard.org/identity.xesext"/>
                <extension name="Lifecycle" prefix="lifecycle" uri="http://www.xes-standard.org/lifecycle.xesext"/>
                <extension name="Time" prefix="time" uri="http://www.xes-standard.org/time.xesext"/>
                <extension name="Organizational" prefix="org" uri="http://www.xes-standard.org/org.xesext"/>
                <extension name="Cost" prefix="cost" uri="http://www.xes-standard.org/cost.xesext"/>
                <string key="concept:name" value="Log concept:name"/>
                <string key="identity:id" value="Log identity:id"/>
                <string key="lifecycle:model" value="standard"/>
                <trace>
                    <string key="concept:name" value="Trace #001"/>
                    <string key="identity:id" value="T-001"/>
                    <float key="cost:total" value="99.99"/>
                    <string key="cost:currency" value="PLN"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #001"/>
                        <string key="identity:id" value="E-001"/>
                        <string key="lifecycle:transition" value="start"/>
                        <date key="time:timestamp" value="2005-01-01T00:00:00.000+01:00"/>
                        <string key="org:group" value="Endoscopy"/>
                        <string key="org:resource" value="Drugs"/>
                        <string key="org:role" value="Intern"/>
                        <float key="cost:total" value="90.99"/>
                        <string key="cost:currency" value="PLN"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #001"/>
                        <string key="identity:id" value="E-002"/>
                        <string key="lifecycle:transition" value="complete"/>
                        <date key="time:timestamp" value="2005-01-03T00:00:00.000+01:00"/>
                        <string key="org:group" value="Endoscopy"/>
                        <string key="org:resource" value="Pills"/>
                        <string key="org:role" value="Assistant"/>
                        <float key="cost:total" value="9.00"/>
                        <string key="cost:currency" value="PLN"/>
                    </event>
                </trace>
                <trace>
                    <string key="concept:name" value="Trace #002"/>
                    <string key="identity:id" value="T-002"/>
                    <float key="cost:total" value="10.00"/>
                    <string key="cost:currency" value="USD"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #002"/>
                        <string key="identity:id" value="E-003"/>
                        <string key="lifecycle:transition" value="schedule"/>
                        <date key="time:timestamp" value="2005-01-04T00:00:00.000+01:00"/>
                        <string key="org:group" value="Radiotherapy"/>
                        <string key="org:resource" value="Pills"/>
                        <string key="org:role" value="Intern"/>
                        <float key="cost:total" value="5.00"/>
                        <string key="cost:currency" value="USD"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #002"/>
                        <string key="identity:id" value="E-004"/>
                        <string key="lifecycle:transition" value="complete"/>
                        <date key="time:timestamp" value="2005-01-05T00:00:00.000+01:00"/>
                        <string key="org:group" value="Radiotherapy"/>
                        <string key="org:resource" value="Drugs"/>
                        <string key="org:role" value="Assistant"/>
                        <float key="cost:total" value="5.00"/>
                        <string key="cost:currency" value="USD"/>
                    </event>
                </trace>
            </log>
        """.trimIndent().byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()
            val receivedLog: Log = iterator.next() as Log

            with(receivedLog.extensions) {
                assertEquals(size, 6)

                assertEquals(getValue("cost").name, "Cost")
                assertEquals(getValue("cost").prefix, "cost")
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #001")
                assertEquals(identityId, "T-001")
                assertEquals(costTotal, 99.99)
                assertEquals(costCurrency, "PLN")
            }

            with(iterator.next() as Event) {
                assertEquals(costTotal, 90.99)
                assertEquals(costCurrency, "PLN")
            }

            with(iterator.next() as Event) {
                assertEquals(costTotal, 9.00)
                assertEquals(costCurrency, "PLN")
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #002")
                assertEquals(identityId, "T-002")
                assertEquals(costTotal, 10.00)
                assertEquals(costCurrency, "USD")
            }

            with(iterator.next() as Event) {
                assertEquals(costTotal, 5.00)
                assertEquals(costCurrency, "USD")
            }

            with(iterator.next() as Event) {
                assertEquals(costTotal, 5.00)
                assertEquals(costCurrency, "USD")
            }
        }
    }

    @Test
    fun `Level X1 = Level D extended with attributes from non-standard XES extensions, attributes without an extension, may conflict on the semantics`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <xesextension name="My own extension" prefix="cost" uri="http://example.com/cost.xesext">
                <event>
                    <int key="level">
                        <alias mapping="EN" name="Example level of this event"/>
                    </int>
                </event>
            </xesextension>
        """.trimIndent().byteInputStream().use { stream ->
            val mock = spyk<XESExtensionLoader>(recordPrivateCalls = true)
            every { mock["openExternalStream"]("http://example.com/cost.xesext") } returns stream
        }

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SX")
        dateFormatter.timeZone = TimeZone.getTimeZone("UTC")
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <extension name="Concept" prefix="concept" uri="http://www.xes-standard.org/concept.xesext"/>
                <extension name="Identity" prefix="identity" uri="http://www.xes-standard.org/identity.xesext"/>
                <extension name="Lifecycle" prefix="lifecycle" uri="http://www.xes-standard.org/lifecycle.xesext"/>
                <extension name="Time" prefix="time" uri="http://www.xes-standard.org/time.xesext"/>
                <extension name="Organizational" prefix="org" uri="http://www.xes-standard.org/org.xesext"/>
                <extension name="Cost" prefix="wascost" uri="http://www.xes-standard.org/cost.xesext"/>
                <extension name="My own extension" prefix="cost" uri="http://example.com/cost.xesext"/>
                <string key="concept:name" value="Log concept:name"/>
                <string key="identity:id" value="Log identity:id"/>
                <string key="lifecycle:model" value="standard"/>
                <string key="value-without-extension" value="some-special-value"/>
                <trace>
                    <string key="concept:name" value="Trace #001"/>
                    <string key="identity:id" value="T-001"/>
                    <float key="wascost:total" value="99.99"/>
                    <string key="wascost:currency" value="PLN"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #001"/>
                        <string key="identity:id" value="E-001"/>
                        <string key="lifecycle:transition" value="start"/>
                        <date key="time:timestamp" value="2005-01-01T00:00:00.000+01:00"/>
                        <string key="org:group" value="Endoscopy"/>
                        <string key="org:resource" value="Drugs"/>
                        <string key="org:role" value="Intern"/>
                        <float key="wascost:total" value="90.99"/>
                        <string key="wascost:currency" value="PLN"/>
                        <int key="cost:level" value="1"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #001"/>
                        <string key="identity:id" value="E-002"/>
                        <string key="lifecycle:transition" value="complete"/>
                        <date key="time:timestamp" value="2005-01-03T00:00:00.000+01:00"/>
                        <string key="org:group" value="Endoscopy"/>
                        <string key="org:resource" value="Pills"/>
                        <string key="org:role" value="Assistant"/>
                        <float key="wascost:total" value="9.00"/>
                        <string key="wascost:currency" value="PLN"/>
                        <int key="cost:level" value="2"/>
                    </event>
                </trace>
                <trace>
                    <string key="concept:name" value="Trace #002"/>
                    <string key="identity:id" value="T-002"/>
                    <float key="wascost:total" value="10.00"/>
                    <string key="wascost:currency" value="USD"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #002"/>
                        <string key="identity:id" value="E-003"/>
                        <string key="lifecycle:transition" value="schedule"/>
                        <date key="time:timestamp" value="2005-01-04T00:00:00.000+01:00"/>
                        <string key="org:group" value="Radiotherapy"/>
                        <string key="org:resource" value="Pills"/>
                        <string key="org:role" value="Intern"/>
                        <float key="wascost:total" value="5.00"/>
                        <string key="wascost:currency" value="USD"/>
                        <int key="cost:level" value="1"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #002"/>
                        <string key="identity:id" value="E-004"/>
                        <string key="lifecycle:transition" value="complete"/>
                        <date key="time:timestamp" value="2005-01-05T00:00:00.000+01:00"/>
                        <string key="org:group" value="Radiotherapy"/>
                        <string key="org:resource" value="Drugs"/>
                        <string key="org:role" value="Assistant"/>
                        <float key="wascost:total" value="5.00"/>
                        <string key="wascost:currency" value="USD"/>
                        <int key="cost:level" value="1"/>
                    </event>
                </trace>
            </log>
        """.trimIndent().byteInputStream().use { stream ->
            val iterator = XMLXESInputStream(stream).iterator()
            val receivedLog: Log = iterator.next() as Log

            with(receivedLog.extensions) {
                assertEquals(size, 7)

                assertEquals(getValue("concept").name, "Concept")
                assertEquals(getValue("concept").prefix, "concept")

                assertEquals(getValue("identity").name, "Identity")
                assertEquals(getValue("identity").prefix, "identity")

                assertEquals(getValue("lifecycle").name, "Lifecycle")
                assertEquals(getValue("lifecycle").prefix, "lifecycle")

                assertEquals(getValue("time").name, "Time")
                assertEquals(getValue("time").prefix, "time")

                assertEquals(getValue("org").name, "Organizational")
                assertEquals(getValue("org").prefix, "org")

                assertEquals(getValue("wascost").name, "Cost")
                assertEquals(getValue("wascost").prefix, "wascost")

                assertEquals(getValue("cost").name, "My own extension")
                assertEquals(getValue("cost").prefix, "cost")
            }

            with(receivedLog) {
                assertEquals(conceptName, "Log concept:name")
                assertEquals(identityId, "Log identity:id")
                assertEquals(lifecycleModel, "standard")
            }

            with(receivedLog.attributes) {
                assertEquals(getValue("value-without-extension").value, "some-special-value")
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #001")
                assertEquals(identityId, "T-001")
                assertEquals(costTotal, 99.99)
                assertEquals(costCurrency, "PLN")

                with(attributes) {
                    assertEquals(getValue("wascost:total").value, 99.99)
                    assertEquals(getValue("wascost:currency").value, "PLN")
                }
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #1 in Trace #001")
                assertEquals(identityId, "E-001")
                assertEquals(lifecycleTransition, "start")
                assertEquals(timeTimestamp, dateFormatter.parse("2005-01-01T00:00:00.000+01:00"))
                assertEquals(orgGroup, "Endoscopy")
                assertEquals(orgResource, "Drugs")
                assertEquals(orgRole, "Intern")
                assertEquals(costTotal, 90.99)
                assertEquals(costCurrency, "PLN")

                with(attributes) {
                    assertEquals(getValue("wascost:total").value, 90.99)
                    assertEquals(getValue("wascost:currency").value, "PLN")
                    assertEquals(getValue("cost:level").value, 1L)
                }
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #2 in Trace #001")
                assertEquals(identityId, "E-002")
                assertEquals(lifecycleTransition, "complete")
                assertEquals(timeTimestamp, dateFormatter.parse("2005-01-03T00:00:00.000+01:00"))
                assertEquals(orgGroup, "Endoscopy")
                assertEquals(orgResource, "Pills")
                assertEquals(orgRole, "Assistant")
                assertEquals(costTotal, 9.00)
                assertEquals(costCurrency, "PLN")

                with(attributes) {
                    assertEquals(getValue("wascost:total").value, 9.00)
                    assertEquals(getValue("wascost:currency").value, "PLN")
                    assertEquals(getValue("cost:level").value, 2L)
                }
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #002")
                assertEquals(identityId, "T-002")
                assertEquals(costTotal, 10.00)
                assertEquals(costCurrency, "USD")

                with(attributes) {
                    assertEquals(getValue("wascost:total").value, 10.00)
                    assertEquals(getValue("wascost:currency").value, "USD")
                }
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #1 in Trace #002")
                assertEquals(identityId, "E-003")
                assertEquals(lifecycleTransition, "schedule")
                assertEquals(timeTimestamp, dateFormatter.parse("2005-01-04T00:00:00.000+01:00"))
                assertEquals(orgGroup, "Radiotherapy")
                assertEquals(orgResource, "Pills")
                assertEquals(orgRole, "Intern")
                assertEquals(costTotal, 5.00)
                assertEquals(costCurrency, "USD")

                with(attributes) {
                    assertEquals(getValue("wascost:total").value, 5.00)
                    assertEquals(getValue("wascost:currency").value, "USD")
                    assertEquals(getValue("cost:level").value, 1L)
                }
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #2 in Trace #002")
                assertEquals(identityId, "E-004")
                assertEquals(lifecycleTransition, "complete")
                assertEquals(timeTimestamp, dateFormatter.parse("2005-01-05T00:00:00.000+01:00"))
                assertEquals(orgGroup, "Radiotherapy")
                assertEquals(orgResource, "Drugs")
                assertEquals(orgRole, "Assistant")
                assertEquals(costTotal, 5.00)
                assertEquals(costCurrency, "USD")

                with(attributes) {
                    assertEquals(getValue("wascost:total").value, 5.00)
                    assertEquals(getValue("wascost:currency").value, "USD")
                    assertEquals(getValue("cost:level").value, 1L)
                }
            }
        }
    }
}