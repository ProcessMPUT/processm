/* eslint-disable prettier/prettier */
<template>
  <v-container>
    <v-row no-gutters>
      <v-col>
        <h1>Process Query Language documentation</h1>
        <h1 id="status-of-this-document">Status of this document</h1>
        <ul>
          <li>
            Version 0.3 (2021-07-20): maintenance update, more details
            explained, text fixes, etc.
          </li>
          <li>
            Version 0.2 (2020-06-16): new standard attributes, the non-standard
            syntax for classifiers, string escaping rules, new syntax for the
            group-by clause, null handling in the order-by clause, error
            handling rules, known limitations, reference implementation
            (current).
          </li>
          <li>Version 0.1 (2020-03-23) - The initial draft specification.</li>
        </ul>
        <h1 id="introduction">Introduction</h1>
        <p>
          Process Query Language (PQL) is a language for querying event logs for
          efficient retrieval of process-related information, such as process
          variants and key performance indicators. A PQL query forms a data
          source for business process analytics tools, such as the tools for
          process discovery, conformance checking, root cause analysis, and
          process enhancement. PQL enables a user to specify a view on a
          collection of event logs available to him or her. PQL is inspired by
          the
          <a href="https://www.iso.org/standard/63555.html"
            >ISO/IEC 9075:2016 standard for Information technology — Database
            languages — SQL</a
          >, however, there are clearly visible differences. This document
          summarizes the data model, syntax, and features of PQL.
        </p>
        <h1 id="case-sensitivity">Case sensitivity</h1>
        <p>
          All keywords, identifiers, and comparisons with values in PQL are
          case-sensitive.
        </p>
        <h1 id="data-model">Data model</h1>
        <p>
          PQL query works on a data structure compatible with the
          <a href="https://standards.ieee.org/standard/1849-2016.html"
            >IEEE 1849-2016 Standard for eXtensible Event Stream for Achieving
            Interoperability in Event Logs and Event Streams</a
          >
          (XES). The data source is a list of XES-conformant logs. Each log is a
          hierarchical structure with three levels. The root of this hierarchy
          is the <code>log</code> component. It is associated with a list of
          <code>trace</code> components. In turn, a trace consists of a list of
          <code>event</code> components. An individual log usually corresponds
          to an individual business process, a trace corresponds to a business
          case in this process, and an event corresponds to an event in this
          business case. Logs, traces, and events consist of
          <a href="#attributes">attributes</a>. This structure is visualized
          using parent-child relations:
        </p>
        <pre><code>logs
\-traces
   \-events</code></pre>
        <p>
          Every <code>event</code> at the <code>events</code> level is a child
          of exactly one <code>trace</code> at the <code>traces</code> level,
          and every <code>trace</code> is a child of exactly one
          <code>log</code> at the <code>logs</code> level.
        </p>
        <p>
          The result of a PQL query is a projection of this structure that
          maintains the same three-level hierarchy of relations.
        </p>
        <h2 id="scopes">Scopes</h2>
        <p>
          A scope is a view on the data model limited to its single part. PQL
          defines the following scopes:
        </p>
        <ul>
          <li><code>log</code> - refers to the <code>logs</code> level,</li>
          <li><code>trace</code> - refers to the <code>traces</code> level,</li>
          <li><code>event</code> - refers to the <code>events</code> level.</li>
        </ul>
        <p>
          PQL defines <strong>shorthand scope names</strong> <code>l</code>,
          <code>t</code>, and <code>e</code> for <code>log</code>,
          <code>trace</code>, and <code>event</code> scopes, respectively.
        </p>
        <p>
          Each time a scope is expected in a PQL query but not specified
          explicitly, the <code>event</code> scope is used.
        </p>
        <h2 id="scope-hoisting">Scope hoisting</h2>
        <p>
          PQL defines a hoisting prefix <code>^</code> for a scope. It moves the
          referenced <a href="#expressions">expression</a> from its scope to its
          parent scope. It is allowed to duplicate <code>^</code> prefix to
          hoist the scope further. E.g., <code>^event</code> moves the scope of
          an expression from the <code>event</code> scope to the
          <code>trace</code> scope, and <code>^^event</code> moves it to the
          <code>log</code> scope. However, it is forbidden to hoist a scope
          beyond the <code>log</code> scope, hence <code>^^^event</code> and
          <code>^^trace</code> are incorrect.
        </p>
        <p>
          Since the parent-child relation is one-to-many, the hoisted expression
          effectively holds the <em>list</em> of its values on its original
          scope.
        </p>
        <p>
          Scope hoisting is useful in filtering using the
          <a href="#the-where-clause"><code>where</code></a> clause, where it
          allows for filtering entries at certain scope using the expressions
          made of the attributes of its children scopes. The
          <code>where</code> clause is satisfied if it holds for any value in
          the list of the values of the hoisted expression.
        </p>
        <p>
          Scope hoisting is also useful in grouping using the
          <a href="#the-group-by-clause"><code>group by</code></a> clause, where
          it allows for grouping of traces into process variants using the
          attributes of the events.
        </p>
        <p>
          Scope hoisting is not supported in the
          <a href="#the-select-clause"><code>select</code></a> and the
          <a href="#the-order-by-clause"><code>order by</code></a> clauses,
          except as an argument to an
          <a href="#aggregation-functions">aggregation function</a>.
        </p>
        <h2 id="data-types">Data types</h2>
        <p>PQL distinguishes the below data types:</p>
        <ul>
          <li><code>uuid</code> - an universally unique identifier,</li>
          <li><code>string</code> - an UTF-8-encoded text,</li>
          <li>
            <code>number</code> - a double-precision floating point number
            compliant with the
            <a href="https://doi.org/10.1109%2FIEEESTD.2019.8766229"
              >IEEE 754-2019 standard</a
            >,
          </li>
          <li>
            <code>datetime</code> - an UTC timestamp with millisecond precision
            compliant with the
            <a href="https://www.iso.org/iso-8601-date-and-time-format.html"
              >ISO 8601-1:2019 standard</a
            >,
          </li>
          <li>
            <code>boolean</code> - a Boolean-algebra <code>true</code> or
            <code>false</code>,
          </li>
          <li><code>any</code> - any of the above-mentioned.</li>
        </ul>
        <p>
          Every data type includes a special <code>null</code> value that
          represents a lack of the actual value. All
          <a href="#comparison-operators">comparisons</a> to
          <code>null</code> yield <code>false</code>, except for a special
          <a href="#comparison-operators"><code>is</code></a> operator.
        </p>
        <p>PQL does not support type casts.</p>
        <h2 id="literals">Literals</h2>
        <p>
          PQL supports the use of literals in queries. The representation of the
          literal depends on its type:
        </p>
        <ul>
          <li>
            A <code>uuid</code> is a universally unique identifier compliant
            with the
            <a href="https://www.iso.org/standard/62795.html"
              >ISO/IEC 9834-8:2014 standard</a
            >, 32 hexadecimal (base-16) digits, displayed in five groups
            separated by hyphens, in the form of 8-4-4-4-12,
          </li>
          <li>
            A <code>string</code> literal is a <code>'single-quoted'</code> or
            <code>"double-quoted"</code> string; backslash <code>\</code> can be
            used as an escape character, see below for the details,
          </li>
          <li>
            A <code>number</code> literal is a valid
            <a href="https://doi.org/10.1109%2FIEEESTD.2019.8766229"
              >IEEE 754-2019</a
            >
            string representation of a number, such as e.g., the decimal point
            number <code>3.14</code> and the scientific notation number
            <code>1.23E45</code>,
          </li>
          <li>
            A <code>datetime</code> literal consists of a prefix
            <code>D</code> and a date and time with timezone in the format
            compliant with the
            <a href="https://www.iso.org/iso-8601-date-and-time-format.html"
              >ISO 8601-1:2019 standard</a
            >, where time and timezone are optional parts, e.g.,
            <code>D2020-03-13T16:45:50.333</code>,
            <code>D2020-03-13T16:45+02:00</code>, <code>D20200313</code>,
            <code>D20200313164550.333</code>,
          </li>
          <li>
            A <code>boolean</code> literal is either <code>true</code> or
            <code>false</code>.
          </li>
        </ul>
        <p>
          The literals are scopeless by default, i.e., they do not change the
          scope of the <a href="#expressions">expression</a>. However, it is
          supported to specify explicitly the scope of the literal using a scope
          prefix, e.g., <code>l:'log-scoped string'</code>,
          <code>t:D2020-03-13T16:45+02.00</code>, and <code>e:3.14</code> are
          valid literals with the scopes specified. If an expression reduces to
          scopeless literals only, i.e., consists of no
          <a href="#attributes">attributes</a>, and no literal has a scope
          associated with, then the default scope of <code>event</code> applies.
        </p>
        <h3 id="escape-sequences-in-string-literals">
          Escape sequences in <code>string</code> literals
        </h3>
        <p>
          The <code>string</code> literals in PQL may contain escape sequences.
          An escape sequence consists of the backslash character
          <code>\</code> and the sequence of one or more characters. The below
          table shows the complete list of available escape sequences.
        </p>
        <table>
          <colgroup>
            <col style="width: 22%" />
            <col style="width: 77%" />
          </colgroup>
          <thead>
            <tr class="header">
              <th>Escape sequence</th>
              <th>Meaning</th>
            </tr>
          </thead>
          <tbody>
            <tr class="odd">
              <td><code>\b</code></td>
              <td>Backspace</td>
            </tr>
            <tr class="even">
              <td><code>\n</code></td>
              <td>New line</td>
            </tr>
            <tr class="odd">
              <td><code>\t</code></td>
              <td>Horizontal tab</td>
            </tr>
            <tr class="even">
              <td><code>\f</code></td>
              <td>Form feed</td>
            </tr>
            <tr class="odd">
              <td><code>\r</code></td>
              <td>Carriage return</td>
            </tr>
            <tr class="even">
              <td><code>\o</code>, <code>\oo</code>, <code>\ooo</code></td>
              <td>
                Octal byte value, where <code>o</code> is a number from the
                range from 0 to 7
              </td>
            </tr>
            <tr class="odd">
              <td><code>\uxxxx</code></td>
              <td>
                16-bit Unicode character, where <code>x</code> is a hexadecimal
                number from the range from 0 to F
              </td>
            </tr>
          </tbody>
        </table>
        <p>
          Any other character following the backslash <code>\</code> is read
          literally. To include a backslash character, write two backslashes
          <code>\\</code>; to include a single-quote in the single-quoted string
          use <code>\'</code>, and to include a double-quote in the
          double-quoted string use <code>\"</code>.
        </p>
        <p>
          It is the user’s responsibility that the byte sequences created using
          the octal and Unicode escape sequences are valid characters in the
          UTF-8 encoding. The <code>\uxxxx</code> escape sequence can be used to
          specify UTF-16 surrogate pairs to compose characters with code points
          larger than <code>\uFFFF</code>.
        </p>
        <h2 id="attributes">Attributes</h2>
        <p>
          The set of attributes available on every scope dynamically adapts to
          data: every attribute supplied by the data source is available for
          use. It is even possible that every log, trace, and event has a
          different set of attributes associated with it.
        </p>
        <h3 id="the-standard-attributes">The standard attributes</h3>
        <p>
          PQL defines <strong>standard attributes</strong> available at all
          times. A name of a standard attribute is a colon-separated list of the
          scope, the extension prefix, and the name of the attribute as defined
          in the
          <a href="https://standards.ieee.org/standard/1849-2016.html"
            >IEEE 1849-2016: IEEE Standard for eXtensible Event Stream (XES) for
            Achieving Interoperability in Event Logs and Event Streams</a
          >.
        </p>
        <p>A complete list of standard attributes is given below:</p>
        <ul>
          <li>
            <code>log:concept:name</code> (type: <code>string</code>) - Stores a
            generally understood name for the log. Usually the name of the
            process having been executed.
          </li>
          <li>
            <code>log:identity:id</code> (type: <code>uuid</code>) - Unique
            identifier (UUID) for the log.
          </li>
          <li>
            <code>log:lifecycle:model</code> (type: <code>string</code>) - This
            attribute refers to the lifecycle transactional model used for all
            events in the log. If this attribute has a value of “standard”, the
            standard lifecycle transactional model is assumed. If it is a value
            of “bpaf”, the Business Process Analytics Format (BPAF) lifecycle
            transactional model is assumed. See the
            <a href="https://standards.ieee.org/standard/1849-2016.html"
              >IEEE 1849-2016</a
            >
            for details.
          </li>
          <li>
            <code>log:xes:version</code> (type: <code>string</code>) - The
            version of the XES standard the log conforms to (e.g.,
            <code>1.0</code>).
          </li>
          <li>
            <code>log:xes:features</code> (type: <code>string</code>) - A
            whitespace-separated list of optional XES features (e.g.,
            <code>nested-attributes</code>). If no optional features are used,
            this attribute shall have an empty value.
          </li>
          <li>
            <code>trace:concept:name</code> (type: <code>string</code>) - Stores
            a generally understood name for the trace. Usually the case ID.
          </li>
          <li>
            <code>trace:cost:currency</code> (type: <code>string</code>) - The
            currency (using the
            <a href="https://www.iso.org/standard/64758.html"
              >ISO 4217:2015 standard</a
            >) of all costs of this trace.
          </li>
          <li>
            <code>trace:cost:total</code> (type: <code>number</code>) - Total
            cost incurred for a trace. The value represents the sum of all the
            cost amounts within the element.
          </li>
          <li>
            <code>trace:identity:id</code> (type: <code>uuid</code>) - Unique
            identifier (UUID) for the trace.
          </li>
          <li>
            <code>trace:classifier:&lt;name&gt;</code> (type:
            <code>string</code>) - Refers the classifier for the
            <code>trace</code> scope using its log-specific
            <code>&lt;name&gt;</code>. The <code>&lt;name&gt;</code> must follow
            the regular expression <code>^[a-zA-Z\u0080-\u00FF_0-9]+$</code>.
            For the classifiers not compliant with this naming, the use of the
            square bracket syntax is required (see below). A classifier is a
            list of attributes, whose values give identity to a trace. See
            <a href="https://standards.ieee.org/standard/1849-2016.html"
              >IEEE 1849-2016</a
            >
            for the details on classifiers. Note that different logs returned by
            the same query may define different classifiers, and so the
            definition of a specific classifier visible to traces from each log
            may be different. The use of this attribute is allowed only in the
            <a href="#the-select-clause"><code>select</code></a
            >, the <a href="#the-group-by-clause"><code>group by</code></a
            >, and the
            <a href="#the-order-by-clause"><code>order by</code></a> clauses,
            where it evaluates to a collection of attributes. It is strictly
            prohibited to use this attribute in other clauses.
          </li>
          <li>
            <code>event:concept:name</code> (type: <code>string</code>) - Stores
            a generally understood name for an event. Usually the name of the
            executed activity represented by the event.
          </li>
          <li>
            <code>event:concept:instance</code> (type: <code>string</code>) -
            This represents an identifier of the activity instance whose
            execution has generated the event. This way, multiple instances
            (occurrences) of the same activity can be told apart.
          </li>
          <li>
            <code>event:cost:currency</code> (type: <code>string</code>) - The
            currency (using the
            <a href="https://www.iso.org/standard/46121.html"
              >ISO 4217:2008 standard</a
            >) of all costs of this event.
          </li>
          <li>
            <code>event:cost:total</code> (type: <code>number</code>) - Total
            cost incurred for an event. The value represents the sum of all the
            cost amounts within the element.
          </li>
          <li>
            <code>event:identity:id</code> (type: <code>uuid</code>) - Unique
            identifier (UUID) for the event.
          </li>
          <li>
            <code>event:lifecycle:transition</code> (type: <code>string</code>)
            - The transition attribute is defined for events, and specifies the
            lifecycle transition of each event. The transitions following the
            <em>standard</em> model should use this attribute. See
            <a href="https://standards.ieee.org/standard/1849-2016.html"
              >IEEE 1849-2016</a
            >
            for the details.
          </li>
          <li>
            <code>event:lifecycle:state</code> (type: <code>string</code>) - The
            state attribute is defined for events and specifies the lifecycle
            state of each event. The transitions following the
            <em>BPAF</em> model should use this attribute. See
            <a href="https://standards.ieee.org/standard/1849-2016.html"
              >IEEE 1849-2016</a
            >
            for the details.
          </li>
          <li>
            <code>event:org:resource</code> (type: <code>string</code>) - The
            name, or identifier, of the resource that triggered the event.
          </li>
          <li>
            <code>event:org:role</code> (type: <code>string</code>) - The role
            of the resource that triggered the event, within the organizational
            structure.
          </li>
          <li>
            <code>event:org:group</code> (type: <code>string</code>) - The group
            within the organizational structure, of which the resource that
            triggered the event is a member.
          </li>
          <li>
            <code>event:time:timestamp</code> (type: <code>datetime</code>) -
            The UTC time at which the event occurred.
          </li>
          <li>
            <code>event:classifier:&lt;name&gt;</code> (type:
            <code>string</code>) - Refers the classifier for the
            <code>event</code> scope using its log-specific
            <code>&lt;name&gt;</code>. The <code>&lt;name&gt;</code> must follow
            the regular expression <code>^[a-zA-Z\u0080-\u00FF_0-9]+$</code>.
            For the classifiers not compliant with this naming, the use of the
            square bracket syntax is required (see below). A classifier is a
            list of attributes, whose values give identity to an event. See
            <a href="https://standards.ieee.org/standard/1849-2016.html"
              >IEEE 1849-2016</a
            >
            for the details on classifiers. Note that different logs returned by
            the same query may define different classifiers, and so the
            definition of a specific classifier visible to events from each log
            may be different. The use of this attribute is allowed only in the
            <a href="#the-select-clause"><code>select</code></a
            >, the <a href="#the-group-by-clause"><code>group by</code></a
            >, and the
            <a href="#the-order-by-clause"><code>order by</code></a> clauses,
            where it evaluates to a collection of attributes. It is forbidden to
            use this attribute in the other clauses.
          </li>
        </ul>
        <p>
          The attributes provided by the data source are translated to these
          standard names using XES extensions as defined in the
          <a href="https://standards.ieee.org/standard/1849-2016.html"
            >IEEE 1849-2016 standard</a
          >, even if the name of the attribute differs in the data source. The
          translation works in three steps:
        </p>
        <ul>
          <li>
            For each log in the data source separately read the log-specified
            prefixes for all standard XES extensions explicitly attached to this
            log. The standard XES extension is recognized by URI, e.g.,
            <code>http://www.xes-standard.org/concept.xesext</code> refers to
            the <code>concept</code> extension. Note that different logs may
            define different prefixes for the same standard attributes.
          </li>
          <li>
            For each log in the data source separately, for each unattached
            standard XES extension attach it with its standard prefix as defined
            in the
            <a href="https://standards.ieee.org/standard/1849-2016.html"
              >IEEE 1849-2016 standard</a
            >,
          </li>
          <li>
            For each log in the data source separately, for each attribute in it
            change its prefix to the standard prefix as defined in the
            <a href="https://standards.ieee.org/standard/1849-2016.html"
              >IEEE 1849-2016 standard</a
            >.
          </li>
        </ul>
        <p>
          Only the attributes from the XES extensions attached to the log are
          translated this way. The standard attributes that do not translate to
          any attribute in the log, are left <code>null</code>. Note that the
          attributes remain available under their original names when using the
          square bracket syntax (see below).
        </p>
        <p>
          Note that the attributes
          <code>trace:classifier:&lt;name&gt;</code> and
          <code>event:classifier:&lt;name&gt;</code> are extracted from the
          collection of XES classifiers provided by the data source with the log
          and their names do not follow that translation rule, as the
          <a href="https://standards.ieee.org/standard/1849-2016.html"
            >IEEE 1849-2016 standard</a
          >
          does not define classifier prefixes. However, the list of the
          attributes corresponding to the classifier follows translation
          accordingly.
        </p>
        <p>
          It is allowed to use <strong>shorthand names</strong> for the standard
          attributes by omitting the standard prefix of the defining XES
          extension and the following colon. E.g.,
          <code>event:concept:name</code> and <code>event:name</code> refer to
          the same attribute. Given the shorthand rules for the
          <a href="#scopes">scopes</a> defined above, <code>name</code>,
          <code>e:name</code>, and <code>e:concept:name</code> refer to
          <code>event:concept:name</code> too. This does not apply to
          <code>trace:classifier:&lt;name&gt;</code> and
          <code>event:classifier:&lt;name&gt;</code>, whose prefixes must not be
          omitted, but can be shortened to <code>c</code>, resulting in
          <code>trace:c:&lt;name&gt;</code> and
          <code>event:c:&lt;name&gt;</code>.
        </p>
        <h3 id="the-bracket-syntax-for-non-standard-attributes">
          The bracket syntax for non-standard attributes
        </h3>
        <p>
          All other attributes are considered non-standard. The non-standard
          attributes are available using the square bracket syntax:
        </p>
        <ul>
          <li>
            <code>[log:&lt;attribute&gt;]</code> - retrieves the
            <code>&lt;attribute&gt;</code> of a log,
          </li>
          <li>
            <code>[trace:&lt;attribute&gt;]</code> - retrieves the
            <code>&lt;attribute&gt;</code> of a trace,
          </li>
          <li>
            <code>[event:&lt;attribute&gt;]</code> - retrieves the
            <code>&lt;attribute&gt;</code> of an event,
          </li>
          <li>
            <code>[trace:classifier:&lt;name&gt;]</code> - retrieves the trace
            classifier with <code>&lt;name&gt;</code>; see above for the details
            on classifiers,
          </li>
          <li>
            <code>[event:classifier:&lt;name&gt;]</code> - retrieves the event
            classifier with <code>&lt;name&gt;</code>; see above for the details
            on classifiers.
          </li>
        </ul>
        <p>
          The square bracket syntax uses the untranslated fully-qualified names
          of the attributes as provided by the data source.
        </p>
        <p>
          The value of an attribute non-existent in a particular component
          referred to using the bracket syntax evaluates to
          <code>null</code> for this component. This behavior simplifies
          operations on components with different sets of attributes. Note that
          the
          <a href="https://standards.ieee.org/standard/1849-2016.html"
            >IEEE 1849-2016 standard</a
          >
          allows for different sets of attributes for every event within the
          same trace, and for every trace within the same log.
        </p>
        <h1 id="identifiers">Identifiers</h1>
        <p>
          An identifier refers to the formal object representing a data item. A
          valid identifier is any of the following:
        </p>
        <ul>
          <li><a href="#attributes">Attribute name</a>.</li>
        </ul>
        <p>
          Note that the term <em>identifier</em>, although equivalent to the
          <em>attribute name</em>, is distinguished as a more general formal
          object reserved for future use.
        </p>
        <h1 id="functions">Functions</h1>
        <p>
          PQL supports <a href="#aggregation-functions">aggregation</a> and
          <a href="#scalar-functions">scalar</a> functions of attributes,
          literals, and an expression thereof. The functions in PQL do not cause
          side effects.
        </p>
        <h2 id="aggregation-functions">Aggregation functions</h2>
        <p>
          PQL supports aggregation functions. For a query with the
          <a href="#the-group-by-clause"><code>group by</code></a> clause, an
          aggregation function yields a single value for each group on each
          scope. For a query without the <code>group by</code> clause, use of
          the aggregation function triggers the
          <a href="#implicit-group-by">implicit group by clause</a>. Aggregation
          functions can be used in the
          <a href="#the-select-clause"><code>select</code></a> and
          <a href="#the-order-by-clause"><code>order by</code></a> clauses. They
          cannot appear in the <a href="#the-where-clause"><code>where</code></a
          >, the <a href="#the-group-by-clause"><code>group by</code></a
          >, the <a href="#the-limit-clause"><code>limit</code></a
          >, and the
          <a href="#the-offset-clause"><code>offset</code></a> clauses.
        </p>
        <p>The complete list of aggregation functions is as follows:</p>
        <ul>
          <li>
            <code>min(any) -&gt; any</code> - Returns the minimum non-null value
            in the set of values of the given attribute for a group, or
            <code>null</code> if such value does not exit. See
            <a href="#comparison-operators">comparison operators</a> for
            details.
          </li>
          <li>
            <code>max(any) -&gt; any</code> - Returns the maximum non-null value
            in the set of values of the given attribute for a group, or
            <code>null</code> if such value does not exit. See
            <a href="#comparison-operators">comparison operators</a> for
            details.
          </li>
          <li>
            <code>avg(number) -&gt; number</code> - Returns the average of
            non-null values of the given attribute for a group.
          </li>
          <li>
            <code>count(any) -&gt; number</code> - Returns the count of non-null
            values of the given attribute for a group.
          </li>
          <li>
            <code>sum(number) -&gt; number</code> - Returns the sum of non-null
            values of the given attribute for a group.
          </li>
        </ul>
        <p>
          The aggregation function can only take an identifier as an argument.
        </p>
        <h2 id="scalar-functions">Scalar functions</h2>
        <p>
          A scalar function takes zero or more scalar arguments and yields a new
          value.
        </p>
        <p>The complete list of scalar functions is as follows:</p>
        <ul>
          <li>
            <code>date(datetime) -&gt; datetime</code> - Returns the date part
            of a datetime (hours, minutes, seconds and milliseconds are zeroed).
          </li>
          <li>
            <code>time(datetime) -&gt; datetime</code> - Returns the time part
            of a datetime (year, month, day are zeroed).
          </li>
          <li>
            <code>year(datetime) -&gt; number</code> - Returns the year from a
            datetime.
          </li>
          <li>
            <code>month(datetime) -&gt; number</code> - Returns the month (1-12)
            from a datetime.
          </li>
          <li>
            <code>day(datetime) -&gt; number</code> - Returns the day of month
            (1-31) from a datetime.
          </li>
          <li>
            <code>hour(datetime) -&gt; number</code> - Returns the hour value
            (0-23) from a datetime.
          </li>
          <li>
            <code>minute(datetime) -&gt; number</code> - Returns the minute
            value (0-59) from a datetime.
          </li>
          <li>
            <code>second(datetime) -&gt; number</code> - Returns the second
            value (0-59) from a datetime.
          </li>
          <li>
            <code>millisecond(datetime) -&gt; number</code> - Returns the
            millisecond value (0-999) from a datetime.
          </li>
          <li>
            <code>quarter(datetime) -&gt; number</code> - Returns the quarter
            (1-4) from a datetime.
          </li>
          <li>
            <code>dayofweek(datetime) -&gt; number</code> - Returns the day of
            week from a datetime. 1 for Sunday, 2 for Monday, 3 for Tuesday etc.
          </li>
          <li>
            <code>now() -&gt; datetime</code> - Returns the datetime of invoking
            of the query; successive calls to this function in the same query
            are guaranteed to return the same value.
          </li>
          <li>
            <code>upper(string) -&gt; string</code> - Converts the given string
            converted to uppercase.
          </li>
          <li>
            <code>lower(string) -&gt; string</code> - Converts the given string
            converted to lowercase.
          </li>
          <li>
            <code>round(number) -&gt; number</code> - Rounds the given number to
            the nearest integer value; rounds half away from zero.
          </li>
        </ul>
        <h1 id="expressions">Expressions</h1>
        <h2 id="arithmetic-expressions">Arithmetic expressions</h2>
        <p>
          PQL defines arithmetic expressions involving addition <code>+</code>,
          subtraction <code>-</code>, multiplication <code>*</code>, and
          division <code>/</code> <a href="#operators">operators</a>. It allows
          for overwriting standard
          <a href="#operator-precedence">precedence rules</a> using the round
          brackets <code>()</code>. An arithmetic expression may use a valid
          identifier, a function call, a scalar value, and any combination
          thereof.
        </p>
        <p>
          E.g., <code>e:cost:total</code>,
          <code>e:cost:total * [e:currency-rate:EURtoUSD]</code>,
          <code>avg(e:cost:total)</code>, and
          <code>(e:cost:total + 10) * [e:currency-rate:EURtoUSD]</code> are
          valid arithmetic expressions.
        </p>
        <h2 id="logical-expressions">Logical expressions</h2>
        <p>
          PQL defines logic expressions involving <code>and</code>,
          <code>or</code>, and <code>not</code>
          <a href="#operators">operators</a>. It allows for overwriting standard
          <a href="#operator-precedence">precedence rules</a> using the round
          brackets <code>()</code>. A logical expression may use
          <code>boolean</code> attributes, literals <code>true</code> and
          <code>false</code>, comparisons of arithmetic expressions, and a
          combination thereof.
        </p>
        <p>
          E.g., <code>e:org:resource = 'scott'</code>,
          <code>e:org:resource = 'scott' or e:org:group = 'helpdesk'</code>, and
          <code
            >(e:org:resource = 'scott' or e:org:group = 'helpdesk') and
            dayofweek(e:time:timestamp) = 1</code
          >
          are valid logic expressions.
        </p>
        <h2 id="properties-of-an-expression">Properties of an expression</h2>
        <p>
          Every expression is characterized by the scope. The scope of the
          expression is calculated in three steps:
        </p>
        <ul>
          <li>
            Collect the set of the scopes with the optional hoisting prefix of
            all attributes, literals, and functions in this expression,
          </li>
          <li>
            Hoist the scopes in this set using the associated hoisting prefix
            wherever set,
          </li>
          <li>
            Select the lowest scope from this set or the
            <code>event</code> scope if empty.
          </li>
        </ul>
        <h1 id="operators">Operators</h1>
        <p>This section summarizes operators available in PQL.</p>
        <h2 id="arithmetic-operators">Arithmetic operators</h2>
        <ul>
          <li>
            <code>number * number -&gt; number</code> - multiplication of two
            numbers.
          </li>
          <li>
            <code>number / number -&gt; number</code> - multiplication of two
            numbers.
          </li>
          <li>
            <code>number + number -&gt; number</code> - addition of two numbers.
          </li>
          <li>
            <code>number - number -&gt; number</code> - subtraction of two
            numbers.
          </li>
        </ul>
        <p>
          All arithmetic operators yield <code>null</code> if any of their
          operands is <code>null</code>.
        </p>
        <h2 id="text-operators">Text operators</h2>
        <ul>
          <li>
            <code>string + string -&gt; string</code> - concatenation of two
            strings.
          </li>
          <li>
            <code>string like string -&gt; boolean</code> - evaluates to
            <code>true</code> if and only if the first string matches from the
            beginning to the end the pattern specified by the second string,
            <code>false</code> otherwise. The pattern consists of any characters
            interleaved with zero or more placeholder characters. The following
            placeholder characters are defined:
            <ul>
              <li><code>_</code> - matches exactly one any character,</li>
              <li>
                <code>%</code> - matches zero or more characters. To match a
                literal <code>_</code> or <code>%</code> without matching other
                characters, the respective character in the pattern must be
                preceded by the escape character <code>\</code>. To match the
                escape character itself, write two escape characters.
              </li>
            </ul>
          </li>
          <li>
            <code>string matches string -&gt; boolean</code> - evaluates to
            <code>true</code> if and only if the first string matches the
            regular expression specified by the second string,
            <code>false</code> otherwise. The match may occur anywhere within
            the string unless the regular expression is explicitly anchored to
            the beginning or the end of the string. The regular expression must
            conform to the
            <a
              href="https://standards.ieee.org/content/ieee-standards/en/standard/1003_1-2017.html"
              >IEEE 1003-1:2017 Standard for Information Technology–Portable
              Operating System Interface (POSIX(R)) Base Specifications</a
            >.
          </li>
        </ul>
        <h2 id="temporal-operators">Temporal operators</h2>
        <ul>
          <li>
            <code>datetime - datetime -&gt; number</code> - subtraction of two
            <code>datetime</code>s, the resulting value is the number of days
            between them with fractional part representing the fraction of the
            day.
          </li>
        </ul>
        <p>
          All temporal operators yield <code>null</code> if any of their
          operands is <code>null</code>.
        </p>
        <h2 id="logic-operators">Logic operators</h2>
        <ul>
          <li>
            <code>boolean and boolean -&gt; boolean</code> - Boolean
            conjunction.
          </li>
          <li>
            <code>boolean or boolean -&gt; boolean</code> - Boolean disjunction.
          </li>
          <li><code>not boolean -&gt; boolean</code> - Boolean negation.</li>
        </ul>
        <p>
          All logic operators yield <code>null</code> if any of their operands
          is <code>null</code>.
        </p>
        <h2 id="comparison-operators">Comparison operators</h2>
        <ul>
          <li>
            <code>any = any -&gt; boolean</code> - Evaluates to
            <code>true</code> if and only if both values have the same type and
            value, <code>false</code> otherwise.
          </li>
          <li>
            <code>any != any -&gt; boolean</code> - Evaluates to
            <code>false</code> if and only if both values have the same type and
            value, <code>true</code> otherwise.
          </li>
          <li>
            <code>any &lt; any -&gt; boolean</code> - Evaluates to
            <code>true</code> if and only if both arguments have the same type
            and the first argument is smaller than the second,
            <code>false</code> otherwise.
          </li>
          <li>
            <code>any &lt;= any -&gt; boolean</code> - Evaluates to
            <code>true</code> if and only if both arguments have the same type
            and the first argument is smaller than or equal to the second,
            <code>false</code> otherwise.
          </li>
          <li>
            <code>any &gt; any -&gt; boolean</code> - Evaluates to
            <code>true</code> if and only if both arguments have the same type
            and the first argument is larger than the second,
            <code>false</code> otherwise.
          </li>
          <li>
            <code>any &gt;= any -&gt; boolean</code> - Evaluates to
            <code>true</code> if and only if both arguments have the same type
            and the first argument is larger than or equal to the second,
            <code>false</code> otherwise.
          </li>
        </ul>
        <p>
          For these operators, <code>false</code> is considered smaller than
          <code>true</code>, numbers are compared using the
          <a href="https://doi.org/10.1109%2FIEEESTD.2019.8766229"
            >IEEE 754-2019</a
          >
          rules, <code>datetime</code>s are compared with earlier being smaller,
          strings are compared alphabetically, with case-sensitivity. Comparison
          to <code>null</code> yields <code>false</code>.
        </p>
        <h2 id="type-independent-operators">Type-independent operators</h2>
        <ul>
          <li>
            <code>any is null -&gt; boolean</code> - Evaluates to
            <code>true</code> if and only if <code>any</code> is
            <code>null</code>, <code>false</code> otherwise.
          </li>
          <li>
            <code>any is not null -&gt; boolean</code> - Evaluates to
            <code>true</code> if and only if <code>any</code> is not
            <code>null</code>, <code>false</code> otherwise.
          </li>
          <li>
            <code>any in (any, any,..., any) -&gt; boolean</code> - Evaluates to
            <code>true</code> if and only if the first <code>any</code>
            <a href="#comparison-operators">equals</a> any of the remaining
            values, <code>false</code> otherwise.
          </li>
          <li>
            <code>any not in (any, any,..., any) -&gt; boolean</code> -
            Evaluates to <code>false</code> if and only if the first
            <code>any</code> <a href="#comparison-operators">equals</a> any of
            the remaining values, <code>true</code> otherwise.
          </li>
        </ul>
        <h2 id="operator-precedence">Operator precedence</h2>
        <p>This table orders the operators in the descending precedence:</p>
        <table>
          <thead>
            <tr class="header">
              <th>Precedence</th>
              <th>Operator</th>
              <th>Associativity</th>
            </tr>
          </thead>
          <tbody>
            <tr class="odd">
              <td>1</td>
              <td><code>*</code>, <code>/</code></td>
              <td>left</td>
            </tr>
            <tr class="even">
              <td>2</td>
              <td><code>+</code>, <code>-</code></td>
              <td>left</td>
            </tr>
            <tr class="odd">
              <td>3</td>
              <td>
                <code>in</code>, <code>not in</code>, <code>like</code>,
                <code>matches</code>
              </td>
              <td>none</td>
            </tr>
            <tr class="even">
              <td>4</td>
              <td>
                <code>=</code>, <code>!=</code>, <code>&lt;</code> ,
                <code>&lt;=</code>, <code>&gt;</code>, <code>&gt;=</code>
              </td>
              <td>none</td>
            </tr>
            <tr class="odd">
              <td>5</td>
              <td><code>is null</code>, <code>is not null</code></td>
              <td>none</td>
            </tr>
            <tr class="even">
              <td>6</td>
              <td><code>not</code></td>
              <td>right</td>
            </tr>
            <tr class="odd">
              <td>7</td>
              <td><code>and</code></td>
              <td>left</td>
            </tr>
            <tr class="even">
              <td>8</td>
              <td><code>or</code></td>
              <td>left</td>
            </tr>
          </tbody>
        </table>
        <h1 id="syntax">Syntax</h1>
        <p>
          A PQL query is a list of the below clauses in the order specified
          below. All clauses are optional. An empty query is a valid query and
          returns all data in the data source.
        </p>
        <ul>
          <li>
            <a href="#the-select-clause"><code>select</code></a>
          </li>
          <li>
            <a href="#the-where-clause"><code>where</code></a>
          </li>
          <li>
            <a href="#the-group-by-clause"><code>group by</code></a>
          </li>
          <li>
            <a href="#the-order-by-clause"><code>order by</code></a>
          </li>
          <li>
            <a href="#the-limit-clause"><code>limit</code></a>
          </li>
          <li>
            <a href="#the-offset-clause"><code>offset</code></a>
          </li>
        </ul>
        <h2 id="the-select-clause">The select clause</h2>
        <p>
          The select clause specifies the attributes to fetch. The select clause
          takes one of the forms below:
        </p>
        <div class="sourceCode" id="cb2">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb2-1"><a href="#cb2-1" aria-hidden="true" tabindex="-1"></a><span class="kw">select</span> <span class="op">*</span></span>
<span id="cb2-2"><a href="#cb2-2" aria-hidden="true" tabindex="-1"></a><span class="kw">select</span> <span class="op">&lt;</span>scope1<span class="op">&gt;</span>:<span class="op">*</span>[, <span class="op">&lt;</span>scope2<span class="op">&gt;</span>:<span class="op">*</span>[, <span class="op">&lt;</span>scope3<span class="op">&gt;</span>:<span class="op">*</span>]]</span>
<span id="cb2-3"><a href="#cb2-3" aria-hidden="true" tabindex="-1"></a><span class="kw">select</span> <span class="op">&lt;</span>expression1<span class="op">&gt;</span>[, <span class="op">&lt;</span>expression2<span class="op">&gt;</span>[, <span class="op">..</span>.[, <span class="op">&lt;</span>expressionN<span class="op">&gt;</span>]]]</span></code></pre>
        </div>
        <p>
          Where the first form selects all available attributes from the data
          model. This is equivalent to not specifying the
          <code>select</code> clause at all. However, for efficient evaluation
          of complex queries on large data sources, it is recommended to specify
          attributes explicitly using the third form.
        </p>
        <p>
          The second form selects all attributes on the given scopes. The third
          form enables us to select specific
          <a href="#expressions">expressions</a> defined using
          <a href="#attributes">attributes</a>,
          <a href="#literals">literals</a>, and
          <a href="#functions">functions</a>. The user is free to combine the
          second and the third form by separating the
          <code>&lt;scope&gt;:*</code>-based selectors from the
          <code>&lt;expression&gt;</code>-based selectors using commas.
        </p>
        <p>
          The expressions in the <code>select</code> clause are evaluated and
          retrieved as attributes associated with the components. The type of
          the attribute corresponds to the type of the expression. The
          implementations are free to assign custom names to the attributes
          created from the expressions.
        </p>
        <p>
          The
          <a href="https://standards.ieee.org/standard/1849-2016.html"
            >IEEE 1849-2016 standard</a
          >
          forbids duplicate attribute names in the same component, except for
          the list attribute. PQL follows this restriction and each time a query
          selects the same attribute twice, e.g., by a direct reference and
          using a classifier, only the first reference is retrieved.
        </p>
        <p>
          E.g., the below query selects the <code>concept:name</code> attribute
          for the <code>log</code> scope, the <code>concept:name</code> and
          <code>cost:currency</code> attributes for the
          <code>trace</code> scope, and the <code>concept:name</code> and
          <code>cost:total</code> attributes for the <code>event</code> scope.
        </p>
        <div class="sourceCode" id="cb3">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb3-1"><a href="#cb3-1" aria-hidden="true" tabindex="-1"></a><span class="kw">select</span> l<span class="ch">:name</span>, t<span class="ch">:name</span>, t<span class="ch">:currency</span>, e<span class="ch">:name</span>, e<span class="ch">:total</span></span></code></pre>
        </div>
        <p>
          The below query selects the <code>concept:name</code> attribute for
          the <code>trace</code> scope, and all attributes for the
          <code>event</code> scope.
        </p>
        <div class="sourceCode" id="cb4">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb4-1"><a href="#cb4-1" aria-hidden="true" tabindex="-1"></a><span class="kw">select</span> t<span class="ch">:name</span>, e:<span class="op">*</span></span></code></pre>
        </div>
        <p>
          The below query selects the attributes defined in the ‘businesscase’
          classifier for the <code>trace</code> scope and defined in the
          <code>activity_resource</code> classifier for the
          <code>event</code> scope.
        </p>
        <div class="sourceCode" id="cb5">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb5-1"><a href="#cb5-1" aria-hidden="true" tabindex="-1"></a><span class="kw">select</span> t<span class="ch">:classifier:businesscase</span>, e<span class="ch">:classifier:activity_resource</span></span></code></pre>
        </div>
        <p>
          The below query selects the minimum, the average, and the maximum of
          the total cost for all traces in the data source.
        </p>
        <div class="sourceCode" id="cb6">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb6-1"><a href="#cb6-1" aria-hidden="true" tabindex="-1"></a><span class="kw">select</span> <span class="fu">min</span>(t<span class="ch">:total</span>), <span class="fu">avg</span>(t<span class="ch">:total</span>), <span class="fu">max</span>(t<span class="ch">:total</span>)</span></code></pre>
        </div>
        <h2 id="the-where-clause">The where clause</h2>
        <p>
          The <code>where</code> clause <em>filters</em> components to be
          fetched by the PQL query. It takes a form of
        </p>
        <div class="sourceCode" id="cb7">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb7-1"><a href="#cb7-1" aria-hidden="true" tabindex="-1"></a><span class="kw">where</span> <span class="op">&lt;</span>logical_expression<span class="op">&gt;</span></span></code></pre>
        </div>
        <p>
          Where <code>&lt;logical_expression&gt;</code> refers to an arbitrary
          <a href="#logical-expression">logical expression</a>.
        </p>
        <p>
          E.g., the below query fetches the logs with traces with events at
          weekends. The logs and traces without weekend events are filtered out.
          The events in workdays are filtered out.
        </p>
        <div class="sourceCode" id="cb8">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb8-1"><a href="#cb8-1" aria-hidden="true" tabindex="-1"></a><span class="kw">where</span> dayofweek(e<span class="ch">:timestamp</span>) <span class="kw">in</span> (<span class="dv">1</span>, <span class="dv">7</span>)</span></code></pre>
        </div>
        <p>
          In contrast, the below query fetches the logs with traces with events
          at weekends, however, it keeps events in other days thanks to
          <a href="#scope-hoisting">scope hoisting</a>. The logs and traces
          without events at weekends are filtered out.
        </p>
        <div class="sourceCode" id="cb9">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb9-1"><a href="#cb9-1" aria-hidden="true" tabindex="-1"></a><span class="kw">where</span> dayofweek(^e<span class="ch">:timestamp</span>) <span class="kw">in</span> (<span class="dv">1</span>, <span class="dv">7</span>)</span></code></pre>
        </div>
        <p>
          The next query fetches the logs with traces with events at weekends.
          The logs without weekend events are filtered out, however, the traces
          without weekend events but in the logs containing the weekend events
          are returned.
        </p>
        <div class="sourceCode" id="cb10">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb10-1"><a href="#cb10-1" aria-hidden="true" tabindex="-1"></a><span class="kw">where</span> dayofweek(^^e<span class="ch">:timestamp</span>) <span class="kw">in</span> (<span class="dv">1</span>, <span class="dv">7</span>)</span></code></pre>
        </div>
        <p>
          The below query selects the logs with traces having the currency of
          their total cost not reported as the currency of their children’s
          events. All events are kept.
        </p>
        <div class="sourceCode" id="cb11">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb11-1"><a href="#cb11-1" aria-hidden="true" tabindex="-1"></a><span class="kw">where</span> <span class="kw">not</span>(t<span class="ch">:currency</span> <span class="op">=</span> ^e<span class="ch">:currency</span>)</span></code></pre>
        </div>
        <p>
          In contrast, the below query filters out the events having the same
          currency as their corresponding traces.
        </p>
        <div class="sourceCode" id="cb12">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb12-1"><a href="#cb12-1" aria-hidden="true" tabindex="-1"></a><span class="kw">where</span> t<span class="ch">:currency</span> <span class="op">!=</span> e<span class="ch">:currency</span></span></code></pre>
        </div>
        <p>
          The below query fetches the logs with traces having the currency of
          their total cost not reported as the currency of their children events
          and the total cost of the trace is <code>null</code>.
        </p>
        <div class="sourceCode" id="cb13">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb13-1"><a href="#cb13-1" aria-hidden="true" tabindex="-1"></a><span class="kw">where</span> <span class="kw">not</span>(t<span class="ch">:currency</span> <span class="op">=</span> ^e<span class="ch">:currency</span>) <span class="kw">and</span> t<span class="ch">:total</span> <span class="kw">is</span> <span class="kw">null</span></span></code></pre>
        </div>
        <h2 id="the-group-by-clause">The group by clause</h2>
        <p>
          The <code>group by</code> clause clusters the components into groups
          having the same values of the given attributes. It is possible to
          specify one or more attributes using the below syntax. If the
          attribute is a classifier, i.e.,
          <code>trace:classifier:&lt;name&gt;</code> or
          <code>event:classifier:&lt;name&gt;</code>, then this attribute
          expands to a list of actual attributes.
        </p>
        <div class="sourceCode" id="cb14">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb14-1"><a href="#cb14-1" aria-hidden="true" tabindex="-1"></a><span class="kw">group</span> <span class="kw">by</span> <span class="op">&lt;</span>attribute1<span class="op">&gt;</span>[, <span class="op">&lt;</span>attribute2<span class="op">&gt;</span>[,<span class="op">..</span>.[, <span class="op">&lt;</span>attribute3<span class="op">&gt;</span>]]]</span></code></pre>
        </div>
        <p>
          The scope of the attribute corresponds to the scope of grouping.
          <a href="#scope-hoisting">Scope hoisting</a> is supported and allows
          for grouping of the parent-scope components using the list of the
          values of the child-scope attributes. E.g., the hoisted attribute
          <code>^event:concept:name</code> allows for grouping of traces into
          process variants based on the list of the values of the
          <code>concept:name</code> attribute of the underlying events. As a
          result, each variant corresponds to the group of traces having the
          same sequence of events.
        </p>
        <p>
          The <code>group by</code> clause with a scope S restricts the
          attributes available in the <code>select</code> and
          <code>order by</code> clauses on scope S and children scopes to the
          attributes specified in this clause. All other attributes may be used
          as arguments for aggregation functions.
        </p>
        <p>
          Note that the query without the
          <a href="#the-select-clause"><code>select</code></a> clause fetches
          all <em>available</em> attributes rather than all attributes. For the
          queries with the <code>group by</code> clause this means that for the
          grouped scope and the lower scopes only the attributes enumerated in
          the <code>group by</code> clause are fetched.
        </p>
        <p>
          E.g., the below query selects all logs in the data source with all
          their attributes, groups the traces into variants using the
          <a href="#attributes">classifier</a>
          <code>event:classifier:activity</code>, and for each trace variant
          selects all events with the attributes listed in the classifier
          definition.
        </p>
        <div class="sourceCode" id="cb15">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb15-1"><a href="#cb15-1" aria-hidden="true" tabindex="-1"></a><span class="kw">group</span> <span class="kw">by</span> ^e<span class="ch">:classifier:activity</span></span></code></pre>
        </div>
        <p>
          The below query selects <code>trace:concept:name</code> for each
          trace, and the sum of total costs for each group of events having the
          same <code>event:concept:name</code> within each trace individually.
        </p>
        <div class="sourceCode" id="cb16">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb16-1"><a href="#cb16-1" aria-hidden="true" tabindex="-1"></a><span class="kw">select</span> t<span class="ch">:name</span>, e<span class="ch">:name</span>, <span class="fu">sum</span>(e<span class="ch">:total</span>)</span>
<span id="cb16-2"><a href="#cb16-2" aria-hidden="true" tabindex="-1"></a><span class="kw">group</span> <span class="kw">by</span> e<span class="ch">:name</span></span></code></pre>
        </div>
        <p>
          The below query seeks for the variants of the traces with the same
          sequence of events, comparing the events using the
          <code>event:concept:name</code> attribute. The events in the resulting
          trace variants contain the <code>event:concept:name</code> attribute
          and the sum of the total costs incurred by all events with the same
          position within the variant.
        </p>
        <div class="sourceCode" id="cb17">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb17-1"><a href="#cb17-1" aria-hidden="true" tabindex="-1"></a><span class="kw">select</span> e<span class="ch">:name</span>, <span class="fu">sum</span>(e<span class="ch">:total</span>)</span>
<span id="cb17-2"><a href="#cb17-2" aria-hidden="true" tabindex="-1"></a><span class="kw">group</span> <span class="kw">by</span> ^e<span class="ch">:name</span>, e<span class="ch">:name</span></span></code></pre>
        </div>
        <p>
          The below query for each group of logs with the same sequence of
          events among all traces, comparing events using the
          <code>event:concept:name</code> attribute, selects the aggregated log.
          In the resulting log, the events contain the
          <code>event:concept:name</code> attribute and the sum of the total
          costs incurred by all events with the same position within this group
          of logs.
        </p>
        <div class="sourceCode" id="cb18">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb18-1"><a href="#cb18-1" aria-hidden="true" tabindex="-1"></a><span class="kw">select</span> e<span class="ch">:name</span>, <span class="fu">sum</span>(e<span class="ch">:total</span>)</span>
<span id="cb18-2"><a href="#cb18-2" aria-hidden="true" tabindex="-1"></a><span class="kw">group</span> <span class="kw">by</span> ^^e<span class="ch">:name</span>, e<span class="ch">:name</span></span></code></pre>
        </div>
        <h3 id="implicit-group-by">Implicit group by</h3>
        <p>
          The use of the
          <a href="#aggregation-functions">aggregation function</a> in the
          <a href="#the-select-clause"><code>select</code></a> or
          <a href="#the-order-by-clause"><code>order by</code></a> clause
          without the use of any attribute of the same scope as this aggregation
          function in the <code>group by</code> clause (or omitting the
          <code>group by</code> clause) implies an implicit
          <code>group by</code> clause for this scope. The implicit
          <code>group by</code> groups all components on this scope into a
          single aggregation component carrying the aggregated data for all
          matching components. The query with the implicit
          <code>group by</code> clause on scope S must aggregate all attributes
          on scope S referenced in the <code>select</code> and
          <code>order by</code> clauses. It is strictly forbidden to use an
          unaggregated attribute together with the implicit
          <code>group by</code> clause.
        </p>
        <p>
          E.g., the below query for each log and for each trace yields a single
          event holding the average total cost and the boundaries of the time
          window of all events in the corresponding trace.
        </p>
        <div class="sourceCode" id="cb19">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb19-1"><a href="#cb19-1" aria-hidden="true" tabindex="-1"></a><span class="kw">select</span> <span class="fu">avg</span>(e<span class="ch">:total</span>), <span class="fu">min</span>(e<span class="ch">:timestamp</span>), <span class="fu">max</span>(e<span class="ch">:timestamp</span>)</span></code></pre>
        </div>
        <p>
          The below query yields a single log holding the average cost incurred
          by and the boundaries of the time window of all events belonging to
          all logs in the data source.
        </p>
        <div class="sourceCode" id="cb20">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb20-1"><a href="#cb20-1" aria-hidden="true" tabindex="-1"></a><span class="kw">select</span> <span class="fu">avg</span>(^^e<span class="ch">:total</span>), <span class="fu">min</span>(^^e<span class="ch">:timestamp</span>), <span class="fu">max</span>(^^e<span class="ch">:timestamp</span>)</span></code></pre>
        </div>
        <h2 id="the-order-by-clause">The order by clause</h2>
        <p>
          The <code>order by</code> clause specifies the sorting order of the
          results. Its syntax is as follows:
        </p>
        <div class="sourceCode" id="cb21">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb21-1"><a href="#cb21-1" aria-hidden="true" tabindex="-1"></a><span class="kw">order</span> <span class="kw">by</span> <span class="op">&lt;</span>expression1<span class="op">&gt;</span> [<span class="op">&lt;</span>direction<span class="op">&gt;</span>][, <span class="op">&lt;</span>expression2<span class="op">&gt;</span> [<span class="op">&lt;</span>direction<span class="op">&gt;</span>][,<span class="op">..</span>.[, <span class="op">&lt;</span>expressionN<span class="op">&gt;</span> [<span class="op">&lt;</span>direction<span class="op">&gt;</span>]]]]</span></code></pre>
        </div>
        <p>
          The <code>&lt;expression*&gt;</code> placeholders refer to
          <a href="#arithmetic-expressions">arithmetic expressions</a>, whose
          values are ordering keys. The <code>trace:classifier:*</code> and
          <code>event:classifier:*</code> attributes expand to the list of
          underlying attributes. The <code>&lt;direction&gt;</code> placeholders
          refer to the ordering direction, either <code>asc</code> or
          <code>desc</code> for ascending or descending direction, respectively.
          When omitted, <code>asc</code> is assumed.
        </p>
        <p>
          The ordering is the same as imposed by the
          <a href="#comparison-operators">comparison operators</a>, except that
          the <code>null</code> values are considered greater than all other
          values.
        </p>
        <p>
          By omitting the <code>order by</code> clause, the components are
          returned in the same order as provided by the data source. E.g., the
          order of the traces and events in the XES file.
        </p>
        <p>
          The below query orders the events within a trace ascendingly by their
          timestamps.
        </p>
        <div class="sourceCode" id="cb22">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb22-1"><a href="#cb22-1" aria-hidden="true" tabindex="-1"></a><span class="kw">order</span> <span class="kw">by</span> e<span class="ch">:timestamp</span></span></code></pre>
        </div>
        <p>
          The below query orders the traces descendingly by their total costs
          and the events within each trace ascendingly by their timestamps.
        </p>
        <div class="sourceCode" id="cb23">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb23-1"><a href="#cb23-1" aria-hidden="true" tabindex="-1"></a><span class="kw">order</span> <span class="kw">by</span> t<span class="ch">:total</span> <span class="kw">desc</span>, e<span class="ch">:timestamp</span></span></code></pre>
        </div>
        <h2 id="the-limit-clause">The limit clause</h2>
        <p>
          The limit clause imposes the limit on the number of the returned
          components. It takes the form of:
        </p>
        <div class="sourceCode" id="cb24">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb24-1"><a href="#cb24-1" aria-hidden="true" tabindex="-1"></a><span class="kw">limit</span> <span class="op">&lt;</span><span class="kw">scope</span><span class="op">&gt;</span>:<span class="op">&lt;</span><span class="dt">number</span><span class="op">&gt;</span>[, <span class="op">&lt;</span><span class="kw">scope</span><span class="op">&gt;</span>:<span class="op">&lt;</span><span class="dt">number</span><span class="op">&gt;</span>[, <span class="op">&lt;</span><span class="kw">scope</span><span class="op">&gt;</span>:<span class="op">&lt;</span><span class="dt">number</span><span class="op">&gt;</span>]]</span></code></pre>
        </div>
        <p>
          where the <code>&lt;scope&gt;</code> placeholder refers to the scope
          on which to impose the limit given by the corresponding
          <code>&lt;number&gt;</code> placeholder.
        </p>
        <p>
          E.g., the below query returns at most five logs, at most ten traces
          per log, and at most twenty events per trace.
        </p>
        <div class="sourceCode" id="cb25">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb25-1"><a href="#cb25-1" aria-hidden="true" tabindex="-1"></a><span class="kw">limit</span> l<span class="ch">:5</span>, t<span class="ch">:10</span>, e<span class="ch">:20</span></span></code></pre>
        </div>
        <h2 id="the-offset-clause">The offset clause</h2>
        <p>
          The offset clause skips the given number of the beginning entries. It
          has the below form:
        </p>
        <div class="sourceCode" id="cb26">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb26-1"><a href="#cb26-1" aria-hidden="true" tabindex="-1"></a>offset <span class="op">&lt;</span><span class="kw">scope</span><span class="op">&gt;</span>:<span class="op">&lt;</span><span class="dt">number</span><span class="op">&gt;</span>[, <span class="op">&lt;</span><span class="kw">scope</span><span class="op">&gt;</span>:<span class="op">&lt;</span><span class="dt">number</span><span class="op">&gt;</span>[, <span class="op">&lt;</span><span class="kw">scope</span><span class="op">&gt;</span>:<span class="op">&lt;</span><span class="dt">number</span><span class="op">&gt;</span>]]</span></code></pre>
        </div>
        <p>
          Where the <code>&lt;scope&gt;</code> placeholder refers to the scope
          on which to impose the limit given by the corresponding
          <code>&lt;number&gt;</code> placeholder.
        </p>
        <p>
          E.g., the below query returns all but the first five logs, all but the
          first ten traces per log, and all but twenty events per trace.
        </p>
        <div class="sourceCode" id="cb27">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb27-1"><a href="#cb27-1" aria-hidden="true" tabindex="-1"></a>offset l<span class="ch">:5</span>, t<span class="ch">:10</span>, e<span class="ch">:20</span></span></code></pre>
        </div>
        <p>
          The below query combines <code>limit</code> and
          <code>offset</code> clauses to skip the first ten logs and return at
          most five logs, for each log skip the first twenty traces and return
          at most ten traces, and for each trace skip the first forty events and
          return at most twenty events.
        </p>
        <div class="sourceCode" id="cb28">
          <pre
            class="sourceCode sql"
          ><code class="sourceCode sql"><span id="cb28-1"><a href="#cb28-1" aria-hidden="true" tabindex="-1"></a><span class="kw">limit</span> l<span class="ch">:5</span>, t<span class="ch">:10</span>, e<span class="ch">:20</span></span>
<span id="cb28-2"><a href="#cb28-2" aria-hidden="true" tabindex="-1"></a>offset l<span class="ch">:10</span>, t<span class="ch">:20</span>, e<span class="ch">:40</span></span></code></pre>
        </div>
        <h1 id="comments">Comments</h1>
        <p>
          The comment is a sequence of characters that is not interpreted as PQL
          code. The comments are intended for code documentation. PQL supports
          the C-style inline comments <code>// &lt;comment&gt;</code> and the
          SQL-style inline comments <code>-- &lt;comment&gt;</code> that begin
          from a comment prefix and ends at the closest newline character or end
          of input. PQL also supports the C-style block comments
          <code>/* &lt;comment&gt; */</code>. The block comments may span
          several lines or a part of a single line.
        </p>
        <h1 id="known-limitations">Known limitations</h1>
        <p>
          PQL currently does not support some features of the
          <a href="https://standards.ieee.org/standard/1849-2016.html"
            >IEEE 1849-2016 Standard for eXtensible Event Stream (XES) for
            Achieving Interoperability in Event Logs and Event Streams</a
          >. These features are subject to implementation in a future version of
          PQL. This section summarizes the unsupported features.
        </p>
        <h2 id="nested-attributes">Nested attributes</h2>
        <p>
          The
          <a href="https://standards.ieee.org/standard/1849-2016.html"
            >IEEE 1849-2016 standard</a
          >
          defines <em>nested attributes</em> as an optional feature for
          implementing software, not required for compliance with the IEEE
          1849-2016 standard. In the IEEE 1849-2016 standard, the attributes may
          form a tree, where every node represents a single attribute and the
          root attribute is attached to a log, a trace, or an event.
        </p>
        <p>
          PQL does not support references to the nested attributes. However, the
          <a href="#reference-implementation"
            >reference implementation of PQL</a
          >
          supports selecting the entire tree of attributes wherever the tree
          root attribute is selected. The reference implementation of PQL does
          not use or interpret the values of the nested attributes.
        </p>
        <h2 id="list-attribute">List attribute</h2>
        <p>
          The
          <a href="https://standards.ieee.org/standard/1849-2016.html"
            >IEEE 1849-2016 standard</a
          >
          defines the <em>list attribute</em> as an attribute which value is a
          list of other attributes.
        </p>
        <p>
          PQL does not support references to the elements of the list attribute,
          however, the list attribute itself can be referenced by name. The
          <a href="#reference-implementation"
            >reference implementation of PQL</a
          >
          supports retrieval of the list attribute and its elements wherever the
          list attribute is referenced in the
          <a href="#the-select-clause"><code>select</code></a> clause. In all
          other clauses, the value of the list attribute evaluates to
          <code>null</code>.
        </p>
        <h1 id="reference-implementation">Reference implementation</h1>
        <p>
          <a href="https://processm.cs.put.poznan.pl">ProcessM</a> software
          includes the reference implementation of PQL.
        </p>
      </v-col>
    </v-row>
  </v-container>
</template>

<style scoped>
html {
  line-height: 1.5;
  font-family: Georgia, serif;
  font-size: 20px;
  color: #1a1a1a;
  background-color: #fdfdfd;
}
body {
  margin: 0 auto;
  max-width: 36em;
  padding-left: 50px;
  padding-right: 50px;
  padding-top: 50px;
  padding-bottom: 50px;
  hyphens: auto;
  overflow-wrap: break-word;
  text-rendering: optimizeLegibility;
  font-kerning: normal;
}
@media (max-width: 600px) {
  body {
    font-size: 0.9em;
    padding: 1em;
  }
}
@media print {
  body {
    background-color: transparent;
    color: black;
    font-size: 12pt;
  }
  p,
  h2,
  h3 {
    orphans: 3;
    widows: 3;
  }
  h2,
  h3,
  h4 {
    page-break-after: avoid;
  }
}
p {
  margin: 1em 0;
}
a {
  color: #1a1a1a;
}
a:visited {
  color: #1a1a1a;
}
img {
  max-width: 100%;
}
h1,
h2,
h3,
h4,
h5,
h6 {
  margin-top: 1.4em;
}
h5,
h6 {
  font-size: 1em;
  font-style: italic;
}
h6 {
  font-weight: normal;
}
ol,
ul {
  padding-left: 1.7em;
  margin-top: 1em;
}
li > ol,
li > ul {
  margin-top: 0;
}
blockquote {
  margin: 1em 0 1em 1.7em;
  padding-left: 1em;
  border-left: 2px solid #e6e6e6;
  color: #606060;
}
code {
  font-family: Menlo, Monaco, "Lucida Console", Consolas, monospace;
  font-size: 85%;
  margin: 0;
}
pre {
  margin: 1em 0;
  overflow: auto;
}
pre code {
  padding: 0;
  overflow: visible;
  overflow-wrap: normal;
}
.sourceCode {
  background-color: transparent;
  overflow: visible;
}
hr {
  background-color: #1a1a1a;
  border: none;
  height: 1px;
  margin: 1em 0;
}
table {
  margin: 1em 0;
  border-collapse: collapse;
  width: 100%;
  overflow-x: auto;
  display: block;
  font-variant-numeric: lining-nums tabular-nums;
}
table caption {
  margin-bottom: 0.75em;
}
tbody {
  margin-top: 0.5em;
  border-top: 1px solid #1a1a1a;
  border-bottom: 1px solid #1a1a1a;
}
th {
  border-top: 1px solid #1a1a1a;
  padding: 0.25em 0.5em 0.25em 0.5em;
}
td {
  padding: 0.125em 0.5em 0.25em 0.5em;
}
header {
  margin-bottom: 4em;
  text-align: center;
}
#TOC li {
  list-style: none;
}
#TOC a:not(:hover) {
  text-decoration: none;
}
code {
  white-space: pre-wrap;
}
span.smallcaps {
  font-variant: small-caps;
}
span.underline {
  text-decoration: underline;
}
div.column {
  display: inline-block;
  vertical-align: top;
  width: 50%;
}
div.hanging-indent {
  margin-left: 1.5em;
  text-indent: -1.5em;
}
ul.task-list {
  list-style: none;
}
pre > code.sourceCode {
  white-space: pre;
  position: relative;
}
pre > code.sourceCode > span {
  display: inline-block;
  line-height: 1.25;
}
pre > code.sourceCode > span:empty {
  height: 1.2em;
}
.sourceCode {
  overflow: visible;
}
code.sourceCode > span {
  color: inherit;
  text-decoration: inherit;
}
div.sourceCode {
  margin: 1em 0;
}
pre.sourceCode {
  margin: 0;
}
@media screen {
  div.sourceCode {
    overflow: auto;
  }
}
@media print {
  pre > code.sourceCode {
    white-space: pre-wrap;
  }
  pre > code.sourceCode > span {
    text-indent: -5em;
    padding-left: 5em;
  }
}
pre.numberSource code {
  counter-reset: source-line 0;
}
pre.numberSource code > span {
  position: relative;
  left: -4em;
  counter-increment: source-line;
}
pre.numberSource code > span > a:first-child::before {
  content: counter(source-line);
  position: relative;
  left: -1em;
  text-align: right;
  vertical-align: baseline;
  border: none;
  display: inline-block;
  -webkit-touch-callout: none;
  -webkit-user-select: none;
  -khtml-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
  user-select: none;
  padding: 0 4px;
  width: 4em;
  color: #aaaaaa;
}
pre.numberSource {
  margin-left: 3em;
  border-left: 1px solid #aaaaaa;
  padding-left: 4px;
}
div.sourceCode {
}
@media screen {
  pre > code.sourceCode > span > a:first-child::before {
    text-decoration: underline;
  }
}
code span.al {
  color: #ff0000;
  font-weight: bold;
} /* Alert */
code span.an {
  color: #60a0b0;
  font-weight: bold;
  font-style: italic;
} /* Annotation */
code span.at {
  color: #7d9029;
} /* Attribute */
code span.bn {
  color: #40a070;
} /* BaseN */
code span.bu {
} /* BuiltIn */
code span.cf {
  color: #007020;
  font-weight: bold;
} /* ControlFlow */
code span.ch {
  color: #4070a0;
} /* Char */
code span.cn {
  color: #880000;
} /* Constant */
code span.co {
  color: #60a0b0;
  font-style: italic;
} /* Comment */
code span.cv {
  color: #60a0b0;
  font-weight: bold;
  font-style: italic;
} /* CommentVar */
code span.do {
  color: #ba2121;
  font-style: italic;
} /* Documentation */
code span.dt {
  color: #902000;
} /* DataType */
code span.dv {
  color: #40a070;
} /* DecVal */
code span.er {
  color: #ff0000;
  font-weight: bold;
} /* Error */
code span.ex {
} /* Extension */
code span.fl {
  color: #40a070;
} /* Float */
code span.fu {
  color: #06287e;
} /* Function */
code span.im {
} /* Import */
code span.in {
  color: #60a0b0;
  font-weight: bold;
  font-style: italic;
} /* Information */
code span.kw {
  color: #007020;
  font-weight: bold;
} /* Keyword */
code span.op {
  color: #666666;
} /* Operator */
code span.ot {
  color: #007020;
} /* Other */
code span.pp {
  color: #bc7a00;
} /* Preprocessor */
code span.sc {
  color: #4070a0;
} /* SpecialChar */
code span.ss {
  color: #bb6688;
} /* SpecialString */
code span.st {
  color: #4070a0;
} /* String */
code span.va {
  color: #19177c;
} /* Variable */
code span.vs {
  color: #4070a0;
} /* VerbatimString */
code span.wa {
  color: #60a0b0;
  font-weight: bold;
  font-style: italic;
} /* Warning */
.display.math {
  display: block;
  text-align: center;
  margin: 0.5rem auto;
}
</style>
