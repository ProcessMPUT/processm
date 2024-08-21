# Status of this document

* Version 0.4 (2022-05-20): new `delete` clause.
* Version 0.3 (2021-07-20): maintenance update, more details explained, text fixes, etc.
* Version 0.2 (2020-06-16): new standard attributes, the non-standard syntax for classifiers, string escaping rules, new
  syntax for the group-by clause, null handling in the order-by clause, error handling rules, known limitations,
  reference implementation.
* Version 0.1 (2020-03-23) - The initial draft specification.

# Introduction

Process Query Language (PQL) is a language for querying event logs for efficient retrieval of process-related
information, such as process variants and key performance indicators. A PQL query forms a data source for business
process analytics tools, such as the tools for process discovery, conformance checking, root cause analysis, and process
enhancement. PQL enables a user to specify a view on a collection of event logs available to him or her. PQL is inspired
by
the [ISO/IEC 9075:2016 standard for Information technology — Database languages — SQL](https://www.iso.org/standard/63555.html),
however, there are clearly visible differences. This document summarizes the data model, syntax, and features of PQL.

# Case sensitivity

All keywords, identifiers, and comparisons with values in PQL are case-sensitive.

# Data model

PQL query works on a data structure compatible with
the [IEEE 1849-2016 Standard for eXtensible Event Stream for Achieving Interoperability in Event Logs and Event Streams](https://standards.ieee.org/standard/1849-2016.html) (
XES). The data source is a list of XES-conformant logs. Each log is a hierarchical structure with three levels. The root
of this hierarchy is the `log` component. It is associated with a list of `trace` components. In turn, a trace consists
of a list of `event` components. An individual log usually corresponds to an individual business process, a trace
corresponds to a business case in this process, and an event corresponds to an event in this business case. Logs,
traces, and events consist of [attributes](#attributes). This structure is visualized using parent-child relations:

```
logs
\-traces
   \-events
```

Every `event` at the `events` level is a child of exactly one `trace` at the `traces` level, and every `trace` is a
child of exactly one `log` at the `logs` level.

The result of a PQL query is a projection of this structure that maintains the same three-level hierarchy of relations.

## Scopes

A scope is a view on the data model limited to its single part. PQL defines the following scopes:

* `log` - refers to the `logs` level,
* `trace` - refers to the `traces` level,
* `event` - refers to the `events` level.

PQL defines **shorthand scope names** `l`, `t`, and `e` for `log`, `trace`, and `event` scopes, respectively.

Each time a scope is expected in a PQL query but not specified explicitly, the `event` scope is used.

## Scope hoisting

PQL defines a hoisting prefix `^` for a scope. It moves the referenced [expression](#expressions) from its scope to its
parent scope. It is allowed to duplicate `^` prefix to hoist the scope further. E.g., `^event` moves the scope of an
expression from the `event` scope to the `trace` scope, and `^^event` moves it to the `log` scope. However, it is
forbidden to hoist a scope beyond the `log` scope, hence `^^^event` and `^^trace` are incorrect.

Since the parent-child relation is one-to-many, the hoisted expression effectively holds the *list* of its values on its
original scope.

Scope hoisting is useful in filtering using the [`where`](#the-where-clause) clause, where it allows for filtering
entries at certain scope using the expressions made of the attributes of its children scopes. The `where` clause is
satisfied if it holds for any value in the list of the values of the hoisted expression.

Scope hoisting is also useful in grouping using the [`group by`](#the-group-by-clause) clause, where it allows for
grouping of traces into process variants using the attributes of the events.

Scope hoisting is not supported in the [`select`](#the-select-clause) and the [`order by`](#the-order-by-clause)
clauses, except as an argument to an [aggregation function](#aggregation-functions).

## Data types

PQL distinguishes the below data types:

* `uuid` - an universally unique identifier,
* `string` - an UTF-8-encoded text,
* `number` - a double-precision floating point number compliant with
  the [IEEE 754-2019 standard](https://doi.org/10.1109%2FIEEESTD.2019.8766229),
* `datetime` - an UTC timestamp with millisecond precision compliant with
  the [ISO 8601-1:2019 standard](https://www.iso.org/iso-8601-date-and-time-format.html),
* `boolean` - a Boolean-algebra `true` or `false`,
* `any` - any of the above-mentioned.

Every data type includes a special `null` value that represents a lack of the actual value.
All [comparisons](#comparison-operators) to `null` yield `false`, except for a special [`is`](#comparison-operators)
operator.

PQL does not support type casts.

## Literals

PQL supports the use of literals in queries. The representation of the literal depends on its type:

* A `uuid` is a universally unique identifier compliant with
  the [ISO/IEC 9834-8:2014 standard](https://www.iso.org/standard/62795.html), 32 hexadecimal (base-16) digits,
  displayed in five groups separated by hyphens, in the form of 8-4-4-4-12,
* A `string` literal is a `'single-quoted'` or `"double-quoted"` string; backslash `\` can be used as an escape
  character, see below for the details,
* A `number` literal is a valid [IEEE 754-2019](https://doi.org/10.1109%2FIEEESTD.2019.8766229) string representation of
  a number, such as e.g., the decimal point number `3.14` and the scientific notation number `1.23E45`,
* A `datetime` literal consists of a prefix `D` and a date and time with timezone in the format compliant with
  the [ISO 8601-1:2019 standard](https://www.iso.org/iso-8601-date-and-time-format.html), where time and timezone are
  optional parts, e.g., `D2020-03-13T16:45:50.333`, `D2020-03-13T16:45+02:00`, `D20200313`, `D20200313164550.333`,
* A `boolean` literal is either `true` or `false`.

The literals are scopeless by default, i.e., they do not change the scope of the [expression](#expressions). However, it
is supported to specify explicitly the scope of the literal using a scope prefix, e.g., `l:'log-scoped string'`,
`t:D2020-03-13T16:45+02.00`, and `e:3.14` are valid literals with the scopes specified. If an expression reduces to
scopeless literals only, i.e., consists of no [attributes](#attributes), and no literal has a scope associated with,
then the default scope of `event` applies.

### Escape sequences in `string` literals

The `string` literals in PQL may contain escape sequences. An escape sequence consists of the backslash character `\`
and the sequence of one or more characters. The below table shows the complete list of available escape sequences.

| Escape sequence     | Meaning                                                                                |
|---------------------|----------------------------------------------------------------------------------------|
| `\b`                | Backspace                                                                              |
| `\n`                | New line                                                                               |
| `\t`                | Horizontal tab                                                                         |
| `\f`                | Form feed                                                                              |
| `\r`                | Carriage return                                                                        |
| `\o`, `\oo`, `\ooo` | Octal byte value, where `o` is a number from the range from 0 to 7                     |
| `\uxxxx`            | 16-bit Unicode character, where `x` is a hexadecimal number from the range from 0 to F |

Any other character following the backslash `\` is read literally. To include a backslash character, write two
backslashes `\\`; to include a single-quote in the single-quoted string use `\'`, and to include a double-quote in the
double-quoted string use `\"`.

It is the user's responsibility that the byte sequences created using the octal and Unicode escape sequences are valid
characters in the UTF-8 encoding. The `\uxxxx` escape sequence can be used to specify UTF-16 surrogate pairs to compose
characters with code points larger than `\uFFFF`.

## Attributes

The set of attributes available on every scope dynamically adapts to data: every attribute supplied by the data source
is available for use. It is even possible that every log, trace, and event has a different set of attributes associated
with it.

### The standard attributes

PQL defines **standard attributes** available at all times. A name of a standard attribute is a colon-separated list of
the scope, the extension prefix, and the name of the attribute as defined in
the [IEEE 1849-2016: IEEE Standard for eXtensible Event Stream (XES) for Achieving Interoperability in Event Logs and Event Streams](https://standards.ieee.org/standard/1849-2016.html).

A complete list of standard attributes is given below:

* `log:concept:name` (type: `string`) - Stores a generally understood name for the log. Usually the name of the process
  having been executed.
* `log:identity:id` (type: `uuid`) - Unique identifier (UUID) for the log.
* `log:lifecycle:model` (type: `string`) - This attribute refers to the lifecycle transactional model used for all
  events in the log. If this attribute has a value of "standard", the standard lifecycle transactional model is assumed.
  If it is a value of "bpaf", the Business Process Analytics Format (BPAF) lifecycle transactional model is assumed. See
  the [IEEE 1849-2016](https://standards.ieee.org/standard/1849-2016.html) for details.
* `log:xes:version` (type: `string`) - The version of the XES standard the log conforms to (e.g., `1.0`).
* `log:xes:features` (type: `string`) - A whitespace-separated list of optional XES features (e.g.,
  `nested-attributes`). If no optional features are used, this attribute shall have an empty value.
* `trace:concept:name` (type: `string`) - Stores a generally understood name for the trace. Usually the case ID.
* `trace:cost:currency` (type: `string`) - The currency (using
  the [ISO 4217:2015 standard](https://www.iso.org/standard/64758.html)) of all costs of this trace.
* `trace:cost:total` (type: `number`) - Total cost incurred for a trace. The value represents the sum of all the cost
  amounts within the element.
* `trace:identity:id` (type: `uuid`) - Unique identifier (UUID) for the trace.
* `trace:classifier:<name>` (type: `string`) - Refers the classifier for the `trace` scope using its log-specific
  `<name>`. The `<name>` must follow the regular expression `^[a-zA-Z\u0080-\u00FF_0-9]+$`. For the classifiers not
  compliant with this naming, the use of the square bracket syntax is required (see below). A classifier is a list of
  attributes, whose values give identity to a trace.
  See [IEEE 1849-2016](https://standards.ieee.org/standard/1849-2016.html) for the details on classifiers. Note that
  different logs returned by the same query may define different classifiers, and so the definition of a specific
  classifier visible to traces from each log may be different. The use of this attribute is allowed only in the [
  `select`](#the-select-clause), the [`group by`](#the-group-by-clause), and the [`order by`](#the-order-by-clause)
  clauses, where it evaluates to a collection of attributes. It is strictly prohibited to use this attribute in other
  clauses.
* `event:concept:name` (type: `string`) - Stores a generally understood name for an event. Usually the name of the
  executed activity represented by the event.
* `event:concept:instance` (type: `string`) - This represents an identifier of the activity instance whose execution has
  generated the event. This way, multiple instances (occurrences) of the same activity can be told apart.
* `event:cost:currency` (type: `string`) - The currency (using
  the [ISO 4217:2008 standard](https://www.iso.org/standard/46121.html)) of all costs of this event.
* `event:cost:total` (type: `number`) - Total cost incurred for an event. The value represents the sum of all the cost
  amounts within the element.
* `event:identity:id` (type: `uuid`) - Unique identifier (UUID) for the event.
* `event:lifecycle:transition` (type: `string`) - The transition attribute is defined for events, and specifies the
  lifecycle transition of each event. The transitions following the *standard* model should use this attribute.
  See [IEEE 1849-2016](https://standards.ieee.org/standard/1849-2016.html) for the details.
* `event:lifecycle:state` (type: `string`) - The state attribute is defined for events and specifies the lifecycle state
  of each event. The transitions following the *BPAF* model should use this attribute.
  See [IEEE 1849-2016](https://standards.ieee.org/standard/1849-2016.html) for the details.
* `event:org:resource` (type: `string`) - The name, or identifier, of the resource that triggered the event.
* `event:org:role` (type: `string`) - The role of the resource that triggered the event, within the organizational
  structure.
* `event:org:group` (type: `string`) - The group within the organizational structure, of which the resource that
  triggered the event is a member.
* `event:time:timestamp` (type: `datetime`) - The UTC time at which the event occurred.
* `event:classifier:<name>` (type: `string`) - Refers the classifier for the `event` scope using its log-specific
  `<name>`. The `<name>` must follow the regular expression `^[a-zA-Z\u0080-\u00FF_0-9]+$`. For the classifiers not
  compliant with this naming, the use of the square bracket syntax is required (see below). A classifier is a list of
  attributes, whose values give identity to an event.
  See [IEEE 1849-2016](https://standards.ieee.org/standard/1849-2016.html) for the details on classifiers. Note that
  different logs returned by the same query may define different classifiers, and so the definition of a specific
  classifier visible to events from each log may be different. The use of this attribute is allowed only in the [
  `select`](#the-select-clause), the [`group by`](#the-group-by-clause), and the [`order by`](#the-order-by-clause)
  clauses, where it evaluates to a collection of attributes. It is forbidden to use this attribute in the other clauses.

The attributes provided by the data source are translated to these standard names using XES extensions as defined in
the [IEEE 1849-2016 standard](https://standards.ieee.org/standard/1849-2016.html), even if the name of the attribute
differs in the data source. The translation works in three steps:

* For each log in the data source separately read the log-specified prefixes for all standard XES extensions explicitly
  attached to this log. The standard XES extension is recognized by URI, e.g.,
  `http://www.xes-standard.org/concept.xesext` refers to the `concept` extension. Note that different logs may define
  different prefixes for the same standard attributes.
* For each log in the data source separately, for each unattached standard XES extension attach it with its standard
  prefix as defined in the [IEEE 1849-2016 standard](https://standards.ieee.org/standard/1849-2016.html),
* For each log in the data source separately, for each attribute in it change its prefix to the standard prefix as
  defined in the [IEEE 1849-2016 standard](https://standards.ieee.org/standard/1849-2016.html).

Only the attributes from the XES extensions attached to the log are translated this way. The standard attributes that do
not translate to any attribute in the log, are left `null`. Note that the attributes remain available under their
original names when using the square bracket syntax (see below).

Note that the attributes `trace:classifier:<name>` and `event:classifier:<name>` are extracted from the collection of
XES classifiers provided by the data source with the log and their names do not follow that translation rule, as
the [IEEE 1849-2016 standard](https://standards.ieee.org/standard/1849-2016.html) does not define classifier prefixes.
However, the list of the attributes corresponding to the classifier follows translation accordingly.

It is allowed to use **shorthand names** for the standard attributes by omitting the standard prefix of the defining XES
extension and the following colon. E.g., `event:concept:name` and `event:name` refer to the same attribute. Given the
shorthand rules for the [scopes](#scopes) defined above, `name`, `e:name`, and `e:concept:name` refer to
`event:concept:name` too. This does not apply to `trace:classifier:<name>` and `event:classifier:<name>`, whose prefixes
must not be omitted, but can be shortened to `c`, resulting in `trace:c:<name>` and `event:c:<name>`.

### The bracket syntax for non-standard attributes

All other attributes are considered non-standard. The non-standard attributes are available using the square bracket
syntax:

* `[log:<attribute>]` - retrieves the `<attribute>` of a log,
* `[trace:<attribute>]` - retrieves the `<attribute>` of a trace,
* `[event:<attribute>]` - retrieves the `<attribute>` of an event,
* `[trace:classifier:<name>]` - retrieves the trace classifier with `<name>`; see above for the details on classifiers,
* `[event:classifier:<name>]` - retrieves the event classifier with `<name>`; see above for the details on classifiers.

The square bracket syntax uses the untranslated fully-qualified names of the attributes as provided by the data source.

The value of an attribute non-existent in a particular component referred to using the bracket syntax evaluates to
`null` for this component. This behavior simplifies operations on components with different sets of attributes. Note
that the [IEEE 1849-2016 standard](https://standards.ieee.org/standard/1849-2016.html) allows for different sets of
attributes for every event within the same trace, and for every trace within the same log.

# Identifiers

An identifier refers to the formal object representing a data item. A valid identifier is any of the following:

* [Attribute name](#attributes).

Note that the term *identifier*, although equivalent to the *attribute name*, is distinguished as a more general formal
object reserved for future use.

# Functions

PQL supports [aggregation](#aggregation-functions) and [scalar](#scalar-functions) functions of attributes, literals,
and an expression thereof. The functions in PQL do not cause side effects.

## Aggregation functions

PQL supports aggregation functions. For a query with the [`group by`](#the-group-by-clause) clause, an aggregation
function yields a single value for each group on each scope. For a query without the `group by` clause, use of the
aggregation function triggers the [implicit group by clause](#implicit-group-by).
Aggregation functions can be used in the [`select`](#the-select-clause) and [`order by`](#the-order-by-clause) clauses.
They cannot appear in the [`where`](#the-where-clause), the [`group by`](#the-group-by-clause), the [
`limit`](#the-limit-clause), and the [`offset`](#the-offset-clause) clauses.

The complete list of aggregation functions is as follows:

* `min(any) -> any` - Returns the minimum non-null value in the set of values of the given attribute for a group, or
  `null` if such value does not exit. See [comparison operators](#comparison-operators) for details.
* `max(any) -> any` - Returns the maximum non-null value in the set of values of the given attribute for a group, or
  `null` if such value does not exit. See [comparison operators](#comparison-operators) for details.
* `avg(number) -> number` - Returns the average of non-null values of the given attribute for a group.
* `count(any) -> number` - Returns the count of non-null values of the given attribute for a group.
* `sum(number) -> number` - Returns the sum of non-null values of the given attribute for a group.

The aggregation function can only take an identifier as an argument.

## Scalar functions

A scalar function takes zero or more scalar arguments and yields a new value.

The complete list of scalar functions is as follows:

* `date(datetime) -> datetime` - Returns the date part of a datetime (hours, minutes, seconds and milliseconds are
  zeroed).
* `time(datetime) -> datetime` - Returns the time part of a datetime (year, month, day are zeroed).
* `year(datetime) -> number` - Returns the year from a datetime.
* `month(datetime) -> number` - Returns the month (1-12) from a datetime.
* `day(datetime) -> number` - Returns the day of month (1-31) from a datetime.
* `hour(datetime) -> number` - Returns the hour value (0-23) from a datetime.
* `minute(datetime) -> number` - Returns the minute value (0-59) from a datetime.
* `second(datetime) -> number` - Returns the second value (0-59) from a datetime.
* `millisecond(datetime) -> number` - Returns the millisecond value (0-999) from a datetime.
* `quarter(datetime) -> number` - Returns the quarter (1-4) from a datetime.
* `dayofweek(datetime) -> number` - Returns the day of week from a datetime. 1 for Sunday, 2 for Monday, 3 for Tuesday
  etc.
* `now() -> datetime` - Returns the datetime of invoking of the query; successive calls to this function in the same
  query are guaranteed to return the same value.
* `upper(string) -> string` - Converts the given string converted to uppercase.
* `lower(string) -> string` - Converts the given string converted to lowercase.
* `round(number) -> number` - Rounds the given number to the nearest integer value; rounds half away from zero.

# Expressions

## Arithmetic expressions

PQL defines arithmetic expressions involving addition `+`, subtraction `-`, multiplication `*`, and division
`/` [operators](#operators). It allows for overwriting standard [precedence rules](#operator-precedence) using the round
brackets `()`. An arithmetic expression may use a valid identifier, a function call, a scalar value, and any combination
thereof.

E.g., `e:cost:total`, `e:cost:total * [e:currency-rate:EURtoUSD]`, `avg(e:cost:total)`, and
`(e:cost:total + 10) * [e:currency-rate:EURtoUSD]` are valid arithmetic expressions.

## Logical expressions

PQL defines logic expressions involving `and`, `or`, and `not` [operators](#operators). It allows for overwriting
standard [precedence rules](#operator-precedence) using the round brackets `()`. A logical expression may use `boolean`
attributes, literals `true`  and `false`, comparisons of arithmetic expressions, and a combination thereof.

E.g., `e:org:resource = 'scott'`, `e:org:resource = 'scott' or e:org:group = 'helpdesk'`, and
`(e:org:resource = 'scott' or e:org:group = 'helpdesk') and dayofweek(e:time:timestamp) = 1` are valid logic
expressions.

## Properties of an expression

Every expression is characterized by the scope. The scope of the expression is calculated in three steps:

* Collect the set of the scopes with the optional hoisting prefix of all attributes, literals, and functions in this
  expression,
* Hoist the scopes in this set using the associated hoisting prefix wherever set,
* Select the lowest scope from this set or the `event` scope if empty.

# Operators

This section summarizes operators available in PQL.

## Arithmetic operators

* `number * number -> number` - multiplication of two numbers.
* `number / number -> number` - multiplication of two numbers.
* `number + number -> number` - addition of two numbers.
* `number - number -> number` - subtraction of two numbers.

All arithmetic operators yield `null` if any of their operands is `null`.

## Text operators

* `string + string -> string` - concatenation of two strings.
* `string like string -> boolean` - evaluates to `true` if and only if the first string matches from the beginning to
  the end the pattern specified by the second string, `false` otherwise. The pattern consists of any characters
  interleaved with zero or more placeholder characters. The following placeholder characters are defined:
    * `_` - matches exactly one any character,
    * `%` - matches zero or more characters.
      To match a literal `_` or `%` without matching other characters, the respective character in the pattern must be
      preceded by the escape character `\`. To match the escape character itself, write two escape characters.
* `string matches string -> boolean` - evaluates to `true` if and only if the first string matches the regular
  expression specified by the second string, `false` otherwise. The match may occur anywhere within the string unless
  the regular expression is explicitly anchored to the beginning or the end of the string. The regular expression must
  conform to
  the [IEEE 1003-1:2017 Standard for Information Technology--Portable Operating System Interface (POSIX(R)) Base Specifications](https://standards.ieee.org/content/ieee-standards/en/standard/1003_1-2017.html).

## Temporal operators

* `datetime - datetime -> number` - subtraction of two `datetime`s, the resulting value is the number of days between
  them with fractional part representing the fraction of the day.

All temporal operators yield `null` if any of their operands is `null`.

## Logic operators

* `boolean and boolean -> boolean` - Boolean conjunction.
* `boolean or boolean -> boolean` - Boolean disjunction.
* `not boolean -> boolean` - Boolean negation.

All logic operators yield `null` if any of their operands is `null`.

## Comparison operators

* `any = any -> boolean` - Evaluates to `true` if and only if both values have the same type and value, `false`
  otherwise.
* `any != any -> boolean` - Evaluates to `false` if and only if both values have the same type and value, `true`
  otherwise.
* `any < any -> boolean` - Evaluates to `true` if and only if both arguments have the same type and the first argument
  is smaller than the second, `false` otherwise.
* `any <= any -> boolean` - Evaluates to `true` if and only if both arguments have the same type and the first argument
  is smaller than or equal to the second, `false` otherwise.
* `any > any -> boolean` - Evaluates to `true` if and only if both arguments have the same type and the first argument
  is larger than the second, `false` otherwise.
* `any >= any -> boolean` - Evaluates to `true` if and only if both arguments have the same type and the first argument
  is larger than or equal to the second, `false` otherwise.

For these operators, `false` is considered smaller than `true`, numbers are compared using
the [IEEE 754-2019](https://doi.org/10.1109%2FIEEESTD.2019.8766229) rules, `datetime`s are compared with earlier being
smaller, strings are compared alphabetically, with case-sensitivity. Comparison to `null` yields `false`.

## Type-independent operators

* `any is null -> boolean` - Evaluates to `true` if and only if `any` is `null`, `false` otherwise.
* `any is not null -> boolean` - Evaluates to `true` if and only if `any` is not `null`, `false` otherwise.
* `any in (any, any,..., any) -> boolean` - Evaluates to `true` if and only if the first
  `any` [equals](#comparison-operators) any of the remaining values, `false` otherwise.
* `any not in (any, any,..., any) -> boolean` - Evaluates to `false` if and only if the first
  `any` [equals](#comparison-operators) any of the remaining values, `true` otherwise.

## Operator precedence

This table orders the operators in the descending precedence:

| Precedence | Operator                          | Associativity |
|------------|-----------------------------------|---------------|
| 1          | `*`, `/`                          | left          |
| 2          | `+`, `-`                          | left          |
| 3          | `in`, `not in`, `like`, `matches` | none          |
| 4          | `=`, `!=`, `<` , `<=`, `>`, `>=`  | none          |
| 5          | `is null`, `is not null`          | none          |
| 6          | `not`                             | right         |
| 7          | `and`                             | left          |
| 8          | `or`                              | left          |

# Syntax

A PQL query is a list of the below clauses in the order specified below. All clauses are optional. An empty query is a
valid query and returns all data in the data source.

* [`select`](#the-select-clause) or [`delete`](#the-delete-clause)
* [`where`](#the-where-clause)
* [`group by`](#the-group-by-clause)
* [`order by`](#the-order-by-clause)
* [`limit`](#the-limit-clause)
* [`offset`](#the-offset-clause)

## The select clause

The select clause specifies the attributes to fetch.
The select clause takes one of the forms below:

```sql
select *
select <scope1>:*[, <scope2>:*[, <scope3>:*]]
select <expression1>[, <expression2>[, ...[, <expressionN>]]]
```

Where the first form selects all available attributes from the data model. This is equivalent to not specifying the
`select` clause at all. However, for efficient evaluation of complex queries on large data sources, it is recommended to
specify attributes explicitly using the third form.

The second form selects all attributes on the given scopes. The third form enables us to select
specific [expressions](#expressions) defined using [attributes](#attributes), [literals](#literals),
and [functions](#functions). The user is free to combine the second and the third form by separating the `<scope>:*`
-based selectors from the `<expression>`-based selectors using commas.

The expressions in the `select` clause are evaluated and retrieved as attributes associated with the components. The
type of the attribute corresponds to the type of the expression. The implementations are free to assign custom names to
the attributes created from the expressions.

The [IEEE 1849-2016 standard](https://standards.ieee.org/standard/1849-2016.html) forbids duplicate attribute names in
the same component, except for the list attribute. PQL follows this restriction and each time a query selects the same
attribute twice, e.g., by a direct reference and using a classifier, only the first reference is retrieved.

E.g., the below query selects the `concept:name` attribute for the `log` scope, the `concept:name` and `cost:currency`
attributes for the `trace` scope, and the `concept:name` and `cost:total` attributes for the `event` scope.

```sql
select l:name, t:name, t:currency, e:name, e:total
```

The below query selects the `concept:name` attribute for the `trace` scope, and all attributes for the `event` scope.

```sql
select t:name, e: *
```

The below query selects the attributes defined in the 'businesscase' classifier for the `trace` scope and defined in the
`activity_resource` classifier for the `event` scope.

```sql
select t:classifier:businesscase, e:classifier:activity_resource
```

The below query selects the minimum, the average, and the maximum of the total cost for all traces in the data source.

```sql
select min(t:total), avg(t:total), max(t:total)
```

## The delete clause

The `delete` clause causes the removal of the items returned by the query and their descendants. The deletion of a log
also deletes all traces and events in this log; the deletion of a trace also deletes all events in this traces. The
deletion of an event deletes only this event.
The `delete` clause takes one of the forms:

```sql
delete
delete <scope>
```

The first form is an abbreviation for `delete event`, i.e., `event` is the default scope.

The `delete` clause deletes all returned items, hence the query:

```sql
delete log
```

deletes all event logs, traces, and events.

For the deletion of all traces and their corresponding events in all event logs, without deleting the logs themselves,
use:

```sql
delete trace
```

For the deletion of all events in all traces and all event logs, without deleting the logs and traces themselves, use:

```sql
delete event
```

To filter concrete items for deletion, combine the `delete` clause with the [`where`](#the-where-clause) clause.
The `delete` clause must not be combined with the `select`, `group by` clauses, and the aggregate functions.

## The where clause

The `where` clause *filters* components to be fetched by the PQL query. It takes the form of

```sql
where <logical_expression>
```

Where `<logical_expression>` refers to an arbitrary [logical expression](#logical-expression).

E.g., the below query fetches the logs with traces with events at weekends. The logs and traces without weekend events
are filtered out. The events in workdays are filtered out.

```sql
where dayofweek(e:timestamp) in (1, 7)
```

In contrast, the below query fetches the logs with traces with events at weekends, however, it keeps events in other
days thanks to [scope hoisting](#scope-hoisting). The logs and traces without events at weekends are filtered out.

```sql
where dayofweek(^e:timestamp) in (1, 7)
```

The next query fetches the logs with traces with events at weekends. The logs without weekend events are filtered out,
however, the traces without weekend events but in the logs containing the weekend events are returned.

```sql
where dayofweek(^^e:timestamp) in (1, 7)
```

The below query selects the logs with traces having the currency of their total cost not reported as the currency of
their children's events. All events are kept.

```sql
where not(t:currency = ^e:currency)
```

In contrast, the below query filters out the events having the same currency as their corresponding traces.

```sql
where t:currency != e:currency
```

The below query fetches the logs with traces having the currency of their total cost not reported as the currency of
their children events and the total cost of the trace is `null`.

```sql
where not(t:currency = ^e:currency) and t:total is null
```

## The group by clause

The `group by` clause clusters the components into groups having the same values of the given attributes. It is possible
to specify one or more attributes using the below syntax. If the attribute is a classifier, i.e.,
`trace:classifier:<name>` or `event:classifier:<name>`, then this attribute expands to a list of actual attributes.

```sql
group by <attribute1>[, <attribute2>[,...[, <attribute3>]]]
```

The scope of the attribute corresponds to the scope of grouping. [Scope hoisting](#scope-hoisting) is supported and
allows for grouping of the parent-scope components using the list of the values of the child-scope attributes. E.g., the
hoisted attribute `^event:concept:name` allows for grouping of traces into process variants based on the list of the
values of the `concept:name` attribute of the underlying events. As a result, each variant corresponds to the group of
traces having the same sequence of events.

The `group by` clause with a scope S restricts the attributes available in the `select` and `order by` clauses on scope
S and children scopes to the attributes specified in this clause. All other attributes may be used as arguments for
aggregation functions.

Note that the query without the [`select`](#the-select-clause) clause fetches all *available* attributes rather than all
attributes. For the queries with the `group by` clause this means that for the grouped scope and the lower scopes only
the attributes enumerated in the `group by` clause are fetched.

E.g., the below query selects all logs in the data source with all their attributes, groups the traces into variants
using the [classifier](#attributes) `event:classifier:activity`, and for each trace variant selects all events with the
attributes listed in the classifier definition.

```sql
group by ^e:classifier:activity
```

The below query selects `trace:concept:name` for each trace, and the sum of total costs for each group of events having
the same `event:concept:name` within each trace individually.

```sql
select t:name, e:name, sum(e:total)
group by e:name
```

The below query seeks for the variants of the traces with the same sequence of events, comparing the events using the
`event:concept:name` attribute. The events in the resulting trace variants contain the `event:concept:name` attribute
and the sum of the total costs incurred by all events with the same position within the variant.

```sql
select e:name, sum(e:total)
group by ^e: name, e: name
```

The below query for each group of logs with the same sequence of events among all traces, comparing events using the
`event:concept:name` attribute, selects the aggregated log. In the resulting log, the events contain the
`event:concept:name` attribute and the sum of the total costs incurred by all events with the same position within this
group of logs.

```sql
select e:name, sum(e:total)
group by ^^e:name, e:name
```

### Implicit group by

The use of the [aggregation function](#aggregation-functions) in the [`select`](#the-select-clause) or [
`order by`](#the-order-by-clause) clause without the use of any attribute of the same scope as this aggregation function
in the `group by` clause (or omitting the `group by` clause) implies an implicit `group by` clause for this scope. The
implicit `group by` groups all components on this scope into a single aggregation component carrying the aggregated data
for all matching components. The query with the implicit `group by` clause on scope S must aggregate all attributes on
scope S referenced in the `select` and `order by` clauses. It is strictly forbidden to use an unaggregated attribute
together with the implicit `group by` clause.

E.g., the below query for each log and for each trace yields a single event holding the average total cost and the
boundaries of the time window of all events in the corresponding trace.

```sql
select avg(e:total), min(e:timestamp), max(e:timestamp)
```

The below query yields a single log holding the average cost incurred by and the boundaries of the time window of all
events belonging to all logs in the data source.

```sql
select avg(^^e:total), min(^^e:timestamp), max(^^e:timestamp)
```

## The order by clause

The `order by` clause specifies the sorting order of the results. Its syntax is as follows:

```sql
order by <expression1> [<direction>][, <expression2> [<direction>][,...[, <expressionN> [<direction>]]]]
```

The `<expression*>` placeholders refer to [arithmetic expressions](#arithmetic-expressions), whose values are ordering
keys. The `trace:classifier:*` and `event:classifier:*` attributes expand to the list of underlying attributes. The
`<direction>` placeholders refer to the ordering direction, either `asc` or `desc` for ascending or descending
direction, respectively. When omitted, `asc` is assumed.

The ordering is the same as imposed by the [comparison operators](#comparison-operators), except that the `null` values
are considered greater than all other values.

By omitting the `order by` clause, the components are returned in the same order as provided by the data source. E.g.,
the order of the traces and events in the XES file.

The below query orders the events within a trace ascendingly by their timestamps.

```sql
order by e:timestamp
```

The below query orders the traces descendingly by their total costs and the events within each trace ascendingly by
their timestamps.

```sql
order by t:total desc, e:timestamp
```

## The limit clause

The limit clause imposes the limit on the number of the returned components. It takes the form of:

```sql
limit <scope>:<number>[, <scope>:<number>[, <scope>:<number>]]
```

where the `<scope>` placeholder refers to the scope on which to impose the limit given by the corresponding `<number>`
placeholder.

E.g., the below query returns at most five logs, at most ten traces per log, and at most twenty events per trace.

```sql
limit l:5, t:10, e:20
```

## The offset clause

The offset clause skips the given number of the beginning entries. It has the below form:

```sql
offset <scope>:<number>[, <scope>:<number>[, <scope>:<number>]]
```

Where the `<scope>` placeholder refers to the scope on which to impose the limit given by the corresponding `<number>`
placeholder.

E.g., the below query returns all but the first five logs, all but the first ten traces per log, and all but twenty
events per trace.

```sql
offset l:5, t:10, e:20
```

The below query combines `limit` and `offset` clauses to skip the first ten logs and return at most five logs, for each
log skip the first twenty traces and return at most ten traces, and for each trace skip the first forty events and
return at most twenty events.

```sql
limit l:5, t:10, e:20
offset l:10, t:20, e:40
```

# Comments

The comment is a sequence of characters that is not interpreted as PQL code. The comments are intended for code
documentation.
PQL supports the C-style inline comments `// <comment>` and the SQL-style inline comments `-- <comment>` that begin from
a comment prefix and ends at the closest newline character or end of input. PQL also supports the C-style block comments
`/* <comment> */`. The block comments may span several lines or a part of a single line.

# Known limitations

PQL currently does not support some features of
the [IEEE 1849-2016 Standard for eXtensible Event Stream (XES) for Achieving Interoperability in Event Logs and Event Streams](https://standards.ieee.org/standard/1849-2016.html).
These features are subject to implementation in a future version of PQL. This section summarizes the unsupported
features.

## Nested attributes

The [IEEE 1849-2016 standard](https://standards.ieee.org/standard/1849-2016.html) defines *nested attributes* as an
optional feature for implementing software, not required for compliance with the IEEE 1849-2016 standard. In the IEEE
1849-2016 standard, the attributes may form a tree, where every node represents a single attribute and the root
attribute is attached to a log, a trace, or an event.

PQL does not support references to the nested attributes. However,
the [reference implementation of PQL](#reference-implementation) supports selecting the entire tree of attributes
wherever the tree root attribute is selected. The reference implementation of PQL does not use or interpret the values
of the nested attributes.

## List attribute

The [IEEE 1849-2016 standard](https://standards.ieee.org/standard/1849-2016.html) defines the *list attribute* as an
attribute which value is a list of other attributes.

PQL does not support references to the elements of the list attribute, however, the list attribute itself can be
referenced by name. The [reference implementation of PQL](#reference-implementation) supports retrieval of the list
attribute and its elements wherever the list attribute is referenced in the [`select`](#the-select-clause) clause. In
all other clauses, the value of the list attribute evaluates to `null`.

# Reference implementation

[ProcessM](https://processm.cs.put.poznan.pl) software includes the reference implementation of PQL.
