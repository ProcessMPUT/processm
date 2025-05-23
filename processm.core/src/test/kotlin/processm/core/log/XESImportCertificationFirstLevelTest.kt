package processm.core.log

import io.mockk.every
import io.mockk.spyk
import processm.helpers.time.parseISO8601
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
                <id key="identity:id" value="bbf3f64f-2507-4f0b-a6f8-0113377d69e4"/>
                <trace>
                    <string key="concept:name" value="Trace #001"/>
                    <id key="identity:id" value="ae1a2f41-2d01-479d-b6a3-84f18d790b20"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #001"/>
                        <id key="identity:id" value="1419fcd5-8fed-4272-8037-453213d8b0d1"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #001"/>
                        <id key="identity:id" value="0e461b08-4f5e-4aa2-b0a2-b7779c82b119"/>
                    </event>
                </trace>
                <trace>
                    <string key="concept:name" value="Trace #002"/>
                    <id key="identity:id" value="a192d6c5-683b-4188-8f73-222227dd4796"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #002"/>
                        <id key="identity:id" value="1a912b4d-6c78-4a4e-8e0f-219fcea53ea7"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #002"/>
                        <id key="identity:id" value="8f14c2ec-83eb-4843-9cb4-c45456a5f3cb"/>
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
                assertEquals(UUID.fromString("bbf3f64f-2507-4f0b-a6f8-0113377d69e4"), identityId)
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #001")
                assertEquals(UUID.fromString("ae1a2f41-2d01-479d-b6a3-84f18d790b20"), identityId)
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #1 in Trace #001")
                assertEquals(UUID.fromString("1419fcd5-8fed-4272-8037-453213d8b0d1"), identityId)
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #2 in Trace #001")
                assertEquals(UUID.fromString("0e461b08-4f5e-4aa2-b0a2-b7779c82b119"), identityId)
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #002")
                assertEquals(UUID.fromString("a192d6c5-683b-4188-8f73-222227dd4796"), identityId)
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #1 in Trace #002")
                assertEquals(UUID.fromString("1a912b4d-6c78-4a4e-8e0f-219fcea53ea7"), identityId)
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #2 in Trace #002")
                assertEquals(UUID.fromString("8f14c2ec-83eb-4843-9cb4-c45456a5f3cb"), identityId)
            }
        }
    }

    @Test
    fun `Level A1 = XES Event data that only contains case identifiers and activity names also when not used standard extensions`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <string key="concept:name" value="Log concept:name"/>
                <id key="identity:id" value="bbf3f64f-2507-4f0b-a6f8-0113377d69e4"/>
                <trace>
                    <string key="concept:name" value="Trace #001"/>
                    <id key="identity:id" value="ae1a2f41-2d01-479d-b6a3-84f18d790b20"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #001"/>
                        <id key="identity:id" value="1419fcd5-8fed-4272-8037-453213d8b0d1"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #001"/>
                        <id key="identity:id" value="0e461b08-4f5e-4aa2-b0a2-b7779c82b119"/>
                    </event>
                </trace>
                <trace>
                    <string key="concept:name" value="Trace #002"/>
                    <id key="identity:id" value="a192d6c5-683b-4188-8f73-222227dd4796"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #002"/>
                        <id key="identity:id" value="1a912b4d-6c78-4a4e-8e0f-219fcea53ea7"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #002"/>
                        <id key="identity:id" value="8f14c2ec-83eb-4843-9cb4-c45456a5f3cb"/>
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
                assertEquals(UUID.fromString("bbf3f64f-2507-4f0b-a6f8-0113377d69e4"), identityId)
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #001")
                assertEquals(UUID.fromString("ae1a2f41-2d01-479d-b6a3-84f18d790b20"), identityId)
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #1 in Trace #001")
                assertEquals(UUID.fromString("1419fcd5-8fed-4272-8037-453213d8b0d1"), identityId)
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #2 in Trace #001")
                assertEquals(UUID.fromString("0e461b08-4f5e-4aa2-b0a2-b7779c82b119"), identityId)
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #002")
                assertEquals(UUID.fromString("a192d6c5-683b-4188-8f73-222227dd4796"), identityId)
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #1 in Trace #002")
                assertEquals(UUID.fromString("1a912b4d-6c78-4a4e-8e0f-219fcea53ea7"), identityId)
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #2 in Trace #002")
                assertEquals(UUID.fromString("8f14c2ec-83eb-4843-9cb4-c45456a5f3cb"), identityId)
            }
        }
    }

    @Test
    fun `Level B1 = Level A1 extended with event types (lifecycleTransition attribute) and timestamps (timeTimestamp attribute)`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <extension name="Concept" prefix="concept" uri="http://www.xes-standard.org/concept.xesext"/>
                <extension name="Identity" prefix="identity" uri="http://www.xes-standard.org/identity.xesext"/>
                <extension name="Lifecycle" prefix="lifecycle" uri="http://www.xes-standard.org/lifecycle.xesext"/>
                <extension name="Time" prefix="time" uri="http://www.xes-standard.org/time.xesext"/>
                <string key="concept:name" value="Log concept:name"/>
                <id key="identity:id" value="bbf3f64f-2507-4f0b-a6f8-0113377d69e4"/>
                <string key="lifecycle:model" value="standard"/>
                <trace>
                    <string key="concept:name" value="Trace #001"/>
                    <id key="identity:id" value="ae1a2f41-2d01-479d-b6a3-84f18d790b20"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #001"/>
                        <id key="identity:id" value="1419fcd5-8fed-4272-8037-453213d8b0d1"/>
                        <string key="lifecycle:transition" value="start"/>
                        <date key="time:timestamp" value="2005-01-01T00:00:00.000+01:00"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #001"/>
                        <id key="identity:id" value="0e461b08-4f5e-4aa2-b0a2-b7779c82b119"/>
                        <string key="lifecycle:transition" value="complete"/>
                        <date key="time:timestamp" value="2005-01-03T00:00:00.000+01:00"/>
                    </event>
                </trace>
                <trace>
                    <string key="concept:name" value="Trace #002"/>
                    <id key="identity:id" value="a192d6c5-683b-4188-8f73-222227dd4796"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #002"/>
                        <id key="identity:id" value="1a912b4d-6c78-4a4e-8e0f-219fcea53ea7"/>
                        <string key="lifecycle:transition" value="schedule"/>
                        <date key="time:timestamp" value="2005-01-04T00:00:00.000+01:00"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #002"/>
                        <id key="identity:id" value="8f14c2ec-83eb-4843-9cb4-c45456a5f3cb"/>
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
                assertEquals(
                    timeTimestamp,
                    "2005-01-01T00:00:00.000+01:00".parseISO8601()
                )
            }

            with(iterator.next() as Event) {
                assertEquals(lifecycleTransition, "complete")
                assertEquals(timeTimestamp, "2005-01-03T00:00:00.000+01:00".parseISO8601())
            }

            assert(iterator.next() is Trace)

            with(iterator.next() as Event) {
                assertEquals(lifecycleTransition, "schedule")
                assertEquals(timeTimestamp, "2005-01-04T00:00:00.000+01:00".parseISO8601())
            }

            with(iterator.next() as Event) {
                assertEquals(lifecycleTransition, "complete")
                assertEquals(timeTimestamp, "2005-01-05T00:00:00.000+01:00".parseISO8601())
            }
        }
    }

    @Test
    fun `Level C1 = Level B1 extended with information on resources (orgResource attribute)`() {
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <extension name="Concept" prefix="concept" uri="http://www.xes-standard.org/concept.xesext"/>
                <extension name="Identity" prefix="identity" uri="http://www.xes-standard.org/identity.xesext"/>
                <extension name="Lifecycle" prefix="lifecycle" uri="http://www.xes-standard.org/lifecycle.xesext"/>
                <extension name="Time" prefix="time" uri="http://www.xes-standard.org/time.xesext"/>
                <extension name="Organizational" prefix="org" uri="http://www.xes-standard.org/org.xesext"/>
                <string key="concept:name" value="Log concept:name"/>
                <id key="identity:id" value="bbf3f64f-2507-4f0b-a6f8-0113377d69e4"/>
                <string key="lifecycle:model" value="standard"/>
                <trace>
                    <string key="concept:name" value="Trace #001"/>
                    <id key="identity:id" value="ae1a2f41-2d01-479d-b6a3-84f18d790b20"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #001"/>
                        <id key="identity:id" value="1419fcd5-8fed-4272-8037-453213d8b0d1"/>
                        <string key="lifecycle:transition" value="start"/>
                        <date key="time:timestamp" value="2005-01-01T00:00:00.000+01:00"/>
                        <string key="org:group" value="Endoscopy"/>
                        <string key="org:resource" value="Drugs"/>
                        <string key="org:role" value="Intern"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #001"/>
                        <id key="identity:id" value="0e461b08-4f5e-4aa2-b0a2-b7779c82b119"/>
                        <string key="lifecycle:transition" value="complete"/>
                        <date key="time:timestamp" value="2005-01-03T00:00:00.000+01:00"/>
                        <string key="org:group" value="Endoscopy"/>
                        <string key="org:resource" value="Pills"/>
                        <string key="org:role" value="Assistant"/>
                    </event>
                </trace>
                <trace>
                    <string key="concept:name" value="Trace #002"/>
                    <id key="identity:id" value="a192d6c5-683b-4188-8f73-222227dd4796"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #002"/>
                        <id key="identity:id" value="1a912b4d-6c78-4a4e-8e0f-219fcea53ea7"/>
                        <string key="lifecycle:transition" value="schedule"/>
                        <date key="time:timestamp" value="2005-01-04T00:00:00.000+01:00"/>
                        <string key="org:group" value="Radiotherapy"/>
                        <string key="org:resource" value="Pills"/>
                        <string key="org:role" value="Intern"/>
                    </event>
                    <event>
                        <string key="concept:name" value="Event #2 in Trace #002"/>
                        <id key="identity:id" value="8f14c2ec-83eb-4843-9cb4-c45456a5f3cb"/>
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
        """<?xml version="1.0" encoding="UTF-8" ?>
            <log xes.version="1.0" xes.features="nested-attributes" openxes.version="1.0RC7" xmlns="http://www.xes-standard.org/">
                <extension name="Concept" prefix="concept" uri="http://www.xes-standard.org/concept.xesext"/>
                <extension name="Identity" prefix="identity" uri="http://www.xes-standard.org/identity.xesext"/>
                <extension name="Lifecycle" prefix="lifecycle" uri="http://www.xes-standard.org/lifecycle.xesext"/>
                <extension name="Time" prefix="time" uri="http://www.xes-standard.org/time.xesext"/>
                <extension name="Organizational" prefix="org" uri="http://www.xes-standard.org/org.xesext"/>
                <extension name="Cost" prefix="cost" uri="http://www.xes-standard.org/cost.xesext"/>
                <string key="concept:name" value="Log concept:name"/>
                <id key="identity:id" value="bbf3f64f-2507-4f0b-a6f8-0113377d69e4"/>
                <string key="lifecycle:model" value="standard"/>
                <trace>
                    <string key="concept:name" value="Trace #001"/>
                    <id key="identity:id" value="ae1a2f41-2d01-479d-b6a3-84f18d790b20"/>
                    <float key="cost:total" value="99.99"/>
                    <string key="cost:currency" value="PLN"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #001"/>
                        <id key="identity:id" value="1419fcd5-8fed-4272-8037-453213d8b0d1"/>
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
                        <id key="identity:id" value="0e461b08-4f5e-4aa2-b0a2-b7779c82b119"/>
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
                    <id key="identity:id" value="a192d6c5-683b-4188-8f73-222227dd4796"/>
                    <float key="cost:total" value="10.00"/>
                    <string key="cost:currency" value="USD"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #002"/>
                        <id key="identity:id" value="1a912b4d-6c78-4a4e-8e0f-219fcea53ea7"/>
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
                        <id key="identity:id" value="8f14c2ec-83eb-4843-9cb4-c45456a5f3cb"/>
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
                assertEquals(UUID.fromString("ae1a2f41-2d01-479d-b6a3-84f18d790b20"), identityId)
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
                assertEquals(UUID.fromString("a192d6c5-683b-4188-8f73-222227dd4796"), identityId)
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
                <id key="identity:id" value="bbf3f64f-2507-4f0b-a6f8-0113377d69e4"/>
                <string key="lifecycle:model" value="standard"/>
                <string key="value-without-extension" value="some-special-value"/>
                <trace>
                    <string key="concept:name" value="Trace #001"/>
                    <id key="identity:id" value="ae1a2f41-2d01-479d-b6a3-84f18d790b20"/>
                    <float key="wascost:total" value="99.99"/>
                    <string key="wascost:currency" value="PLN"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #001"/>
                        <id key="identity:id" value="1419fcd5-8fed-4272-8037-453213d8b0d1"/>
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
                        <id key="identity:id" value="0e461b08-4f5e-4aa2-b0a2-b7779c82b119"/>
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
                    <id key="identity:id" value="a192d6c5-683b-4188-8f73-222227dd4796"/>
                    <float key="wascost:total" value="10.00"/>
                    <string key="wascost:currency" value="USD"/>
                    <event>
                        <string key="concept:name" value="Event #1 in Trace #002"/>
                        <id key="identity:id" value="1a912b4d-6c78-4a4e-8e0f-219fcea53ea7"/>
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
                        <id key="identity:id" value="8f14c2ec-83eb-4843-9cb4-c45456a5f3cb"/>
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
                assertEquals(UUID.fromString("bbf3f64f-2507-4f0b-a6f8-0113377d69e4"), identityId)
                assertEquals(lifecycleModel, "standard")
            }

            with(receivedLog.attributes) {
                assertEquals(getValue("value-without-extension"), "some-special-value")
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #001")
                assertEquals(UUID.fromString("ae1a2f41-2d01-479d-b6a3-84f18d790b20"), identityId)
                assertEquals(costTotal, 99.99)
                assertEquals(costCurrency, "PLN")

                with(attributes) {
                    assertEquals(getValue("wascost:total"), 99.99)
                    assertEquals(getValue("wascost:currency"), "PLN")
                }
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #1 in Trace #001")
                assertEquals(UUID.fromString("1419fcd5-8fed-4272-8037-453213d8b0d1"), identityId)
                assertEquals(lifecycleTransition, "start")
                assertEquals(timeTimestamp, "2005-01-01T00:00:00.000+01:00".parseISO8601())
                assertEquals(orgGroup, "Endoscopy")
                assertEquals(orgResource, "Drugs")
                assertEquals(orgRole, "Intern")
                assertEquals(costTotal, 90.99)
                assertEquals(costCurrency, "PLN")

                with(attributes) {
                    assertEquals(getValue("wascost:total"), 90.99)
                    assertEquals(getValue("wascost:currency"), "PLN")
                    assertEquals(getValue("cost:level"), 1L)
                }
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #2 in Trace #001")
                assertEquals(UUID.fromString("0e461b08-4f5e-4aa2-b0a2-b7779c82b119"), identityId)
                assertEquals(lifecycleTransition, "complete")
                assertEquals(timeTimestamp, "2005-01-03T00:00:00.000+01:00".parseISO8601())
                assertEquals(orgGroup, "Endoscopy")
                assertEquals(orgResource, "Pills")
                assertEquals(orgRole, "Assistant")
                assertEquals(costTotal, 9.00)
                assertEquals(costCurrency, "PLN")

                with(attributes) {
                    assertEquals(getValue("wascost:total"), 9.00)
                    assertEquals(getValue("wascost:currency"), "PLN")
                    assertEquals(getValue("cost:level"), 2L)
                }
            }

            with(iterator.next() as Trace) {
                assertEquals(conceptName, "Trace #002")
                assertEquals(UUID.fromString("a192d6c5-683b-4188-8f73-222227dd4796"), identityId)
                assertEquals(costTotal, 10.00)
                assertEquals(costCurrency, "USD")

                with(attributes) {
                    assertEquals(getValue("wascost:total"), 10.00)
                    assertEquals(getValue("wascost:currency"), "USD")
                }
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #1 in Trace #002")
                assertEquals(UUID.fromString("1a912b4d-6c78-4a4e-8e0f-219fcea53ea7"), identityId)
                assertEquals(lifecycleTransition, "schedule")
                assertEquals(timeTimestamp, "2005-01-04T00:00:00.000+01:00".parseISO8601())
                assertEquals(orgGroup, "Radiotherapy")
                assertEquals(orgResource, "Pills")
                assertEquals(orgRole, "Intern")
                assertEquals(costTotal, 5.00)
                assertEquals(costCurrency, "USD")

                with(attributes) {
                    assertEquals(getValue("wascost:total"), 5.00)
                    assertEquals(getValue("wascost:currency"), "USD")
                    assertEquals(getValue("cost:level"), 1L)
                }
            }

            with(iterator.next() as Event) {
                assertEquals(conceptName, "Event #2 in Trace #002")
                assertEquals(UUID.fromString("8f14c2ec-83eb-4843-9cb4-c45456a5f3cb"), identityId)
                assertEquals(lifecycleTransition, "complete")
                assertEquals(timeTimestamp, "2005-01-05T00:00:00.000+01:00".parseISO8601())
                assertEquals(orgGroup, "Radiotherapy")
                assertEquals(orgResource, "Drugs")
                assertEquals(orgRole, "Assistant")
                assertEquals(costTotal, 5.00)
                assertEquals(costCurrency, "USD")

                with(attributes) {
                    assertEquals(getValue("wascost:total"), 5.00)
                    assertEquals(getValue("wascost:currency"), "USD")
                    assertEquals(getValue("cost:level"), 1L)
                }
            }
        }
    }
}
