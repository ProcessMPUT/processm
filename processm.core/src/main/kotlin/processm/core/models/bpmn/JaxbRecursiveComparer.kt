package processm.core.models.bpmn

import org.w3c.dom.Element
import processm.core.models.bpmn.jaxb.TDefinitions
import java.lang.reflect.Field
import javax.xml.bind.JAXBElement
import javax.xml.bind.annotation.*

class JaxbRecursiveComparer {

    private val seen = HashSet<Pair<Any, Any>>()

    private fun processProp(left: Any, right: Any, prop: String, clazz: Class<in Any>): Boolean {
        val getters = clazz.declaredMethods
            .filter { m -> m.canAccess(left) && m.canAccess(right) }
            .filter { m -> m.parameterCount == 0 }
            .filter { m ->
                m.name.toLowerCase() in setOf(
                    "get${prop.toLowerCase()}",
                    "is${prop.toLowerCase()}",
                    "has${prop.toLowerCase()}"
                )
            }
        return getters.isNotEmpty() && getters.any { getter ->
            this(getter.invoke(left), getter.invoke(right))
        }
    }

    private fun processField(field: Field): Iterable<String> {
        return field.declaredAnnotations.filterIsInstance<XmlElement>().map { ann -> ann.name } +
                field.declaredAnnotations.filterIsInstance<XmlAttribute>().map { ann -> ann.name } +
                field.declaredAnnotations.filterIsInstance<XmlElementRef>().map { ann -> ann.name } +
                field.declaredAnnotations.filterIsInstance<XmlAnyAttribute>().map { field.name }
    }

    private fun compareSameClass(left: Any, right: Any): Boolean {
        var clazz: Class<in Any>? = left.javaClass
        var result = true
        while (result && clazz != null && clazz.packageName == TDefinitions::class.java.packageName) {
            var props: Iterable<String> = clazz.annotations
                .filterIsInstance<XmlType>()
                .flatMap { clzAnn -> clzAnn.propOrder.toSet() }
            props += clazz.declaredFields
                .flatMap { field -> processField(field) }
            result = result && props
                .toSet()
                .filter { it.isNotEmpty() && it[0].isLetter() }
                .all { prop -> processProp(left, right, prop, clazz!!) }
            clazz = clazz.superclass
        }
        return result
    }

    private fun compareOther(left: Any, right: Any): Boolean {
        if (left is Element && right is Element) {
            return left.toString() == right.toString()
        }
        if (left is String && right is String) {
            val normalizer = Regex("\\s+")
            return normalizer.replace(left, " ") == normalizer.replace(right, " ")
        }
        return left == right
    }

    operator fun invoke(_left: Any?, _right: Any?): Boolean {
        if (_left == null || _right == null)
            return _left == null && _right == null
        var left: Any = _left
        var right: Any = _right
        if (left is JAXBElement<*>)
            left = left.value
        if (right is JAXBElement<*>)
            right = right.value
        if (left is Iterable<*> && right is Iterable<*>) {
            return (left zip right).all { (l, r) -> this(l, r) }
        } else if (left.javaClass.packageName != TDefinitions::class.java.packageName || right.javaClass.packageName != TDefinitions::class.java.packageName) {
            return compareOther(left, right)
        } else if (left::class == right::class) {
            if (Pair(left, right) in seen || Pair(right, left) in seen) {
                return true
            } else {
                seen.add(Pair(left, right))
                return compareSameClass(left, right)
            }
        } else {
            return false
        }
    }
}