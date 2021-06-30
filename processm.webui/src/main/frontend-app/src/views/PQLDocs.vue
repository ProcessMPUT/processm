/* eslint-disable prettier/prettier */
<template>
  <v-container>
    <v-row no-gutters>
      <v-col>
        <h1 class="display-1">PQL Documentation</h1>
        <div class="ma-2" align="justify">
          <h1 class="headline">Introduction</h1>
          <p>
            Process Query Language (PQL) is a language for querying event logs
            for efficient retrieval of process-related information, such as
            process variants and key performance indicators. A PQL query forms a
            data source for business process analytics tools, such as the tools
            for process discovery, conformance checking, root cause analysis,
            and process enhancement. PQL enables a user to specify a view on a
            collection of event logs available to him or her. PQL is inspired by
            <a
              href="https://www.iso.org/standard/63555.html"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >ISO/IEC 9075:2016 standard for Information technology — Database
              languages — SQL</a
            >
            and
            <a
              href="https://developers.google.com/chart/interactive/docs/querylanguage"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >Google Visualization API Query Language version 0.7</a
            >, however, there are clearly-visible differences. This document
            summarizes the data model, syntax, and features of PQL.
          </p>
          <h1 class="headline">Case sensitivity</h1>
          <p>
            All keywords, identifiers, and comparisons with values in PQL are
            case-sensitive.
          </p>
          <h1 class="headline">Data model</h1>
          <p>
            PQL query works on a data structure compatible with the
            <a
              href="https://standards.ieee.org/standard/1849-2016.html"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >IEEE 1849-2016 Standard for eXtensible Event Stream (XES) for
              Achieving Interoperability in Event Logs and Event Streams</a
            >. The data source is a collection of XES-conformant logs. Each log
            is a hierarchical structure with three levels. The root of this
            hierarchy is the <code>log</code> component. It is associated with a
            collection of <code>trace</code> components. In turn, a trace
            consists of a collection of <code>event</code> components. An
            individual log usually corresponds to an individual business
            process, a trace corresponds to a business case in this process, and
            an event corresponds to an event in this business case. Logs,
            traces, and events consist of <a href="#attributes">attributes</a>.
            This structure is visualized using parent-child relations:
          </p>
          <pre
            class="code highlight js-syntax-highlight plaintext solarized-dark"
            lang="plaintext"
          ><code><span id="LC1" class="line" lang="plaintext">logs</span>
<span id="LC2" class="line" lang="plaintext">\-traces</span>
<span id="LC3" class="line" lang="plaintext">   \-events</span></code></pre>
          <p>
            I.e., every <code>event</code> on the <code>events</code> level is a
            child of exactly one <code>trace</code> in the
            <code>traces</code> level, and every <code>trace</code> is a child
            of exactly one <code>log</code> in the <code>logs</code> level.
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
            <li data-sourcepos="26:1-26:33">
              <code>log</code> - refers to <code>logs</code> level,
            </li>
            <li data-sourcepos="27:1-27:37">
              <code>trace</code> - refers to <code>traces</code> level,
            </li>
            <li data-sourcepos="28:1-29:0">
              <code>event</code> - refers to <code>events</code> level.
            </li>
          </ul>
          <p>
            PQL defines <strong>shorthand scope names</strong> <code>l</code>,
            <code>t</code>, and <code>e</code> for <code>log</code>,
            <code>trace</code>, and <code>event</code> scopes, respectively.
          </p>
          <p>
            Each time a scope is expected in a PQL query but not specified
            explicitly, the <code>event</code> scope is assumed.
          </p>
          <h2 id="scope-hoisting">Scope hoisting</h2>
          <p>
            PQL defines a hoisting prefix <code>^</code> for a scope. It moves
            the referenced <a href="#expressions">expression</a> from its scope
            to its parent scope. It is allowed to duplicate
            <code>^</code> prefix to hoist the scope further. E.g.,
            <code>^event</code> moves the scope of an expression from the
            <code>event</code> scope to the <code>trace</code> scope, and
            <code>^^event</code> moves it to the <code>log</code> scope.
            However, it is not allowed to hoist a scope beyond the
            <code>log</code> scope, hence <code>^^^event</code> and
            <code>^^trace</code> are incorrect.
          </p>
          <p>
            Since the parent-child relation is one-to-many, the hoisted
            expression effectively holds <em>the ordered list</em> of its values
            on its original scope.
          </p>
          <p>
            Scope hoisting is useful in filtering using the
            <a href="#the-where-clause"><code>where</code></a> clause, where it
            allows for filtering entries at certain scope using the expressions
            made of the attributes of its children scopes. The
            <code>where</code> clause is considered satisfied if it holds for at
            least one of the list of values represented by the hoisted
            expression.
          </p>
          <p>
            Scope hoisting is also useful in grouping using the
            <a href="#the-group-by-clause"><code>group by</code></a>
            clause, where it allows for grouping of traces into process variants
            using the attributes of the events.
          </p>
          <p>
            Scope hoisting is not supported in the
            <a href="#the-select-clause"><code>select</code></a> and the
            <a href="#the-order-by-clause"><code>order by</code></a>
            clauses, except as an argument to an
            <a href="#aggregation-functions">aggregation function</a>.
          </p>
          <h2>Data types</h2>
          <p>PQL distinguishes the below data types:</p>
          <ul>
            <li data-sourcepos="49:1-49:44">
              <code>uuid</code> - an universally unique identifier,
            </li>
            <li data-sourcepos="50:1-50:35">
              <code>string</code> - an UTF-8-encoded text,
            </li>
            <li data-sourcepos="51:1-51:146">
              <code>number</code> - a double precision floating point number
              compliant with the
              <a
                href="https://doi.org/10.1109%2FIEEESTD.2019.8766229"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >IEEE 754-2019 standard</a
              >,
            </li>
            <li data-sourcepos="52:1-52:161">
              <code>datetime</code> - an UTC timestamp with millisecond
              precision compliant with the
              <a
                href="https://www.iso.org/iso-8601-date-and-time-format.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >ISO 8601-1:2019 standard</a
              >,
            </li>
            <li data-sourcepos="53:1-53:50">
              <code>boolean</code> - a Boolean-algebra <code>true</code> or
              <code>false</code>,
            </li>
            <li data-sourcepos="54:1-55:0">
              <code>any</code> - any of the above-mentioned.
            </li>
          </ul>
          <p>
            Every data type includes a special <code>null</code> value that
            represents lack of actual value. All
            <a href="#comparison-operators">comparisons</a> to
            <code>null</code> yield <code>false</code>, except for a special
            <a href="#comparison-operators"><code>is</code></a>
            operator.
          </p>
          <p>PQL does not support type casts.</p>
          <h2 id="literals">Literals</h2>
          <p>
            PQL supports the use of literals in queries. The representation of
            the literal depends on its type:
          </p>
          <ul>
            <li data-sourcepos="63:1-63:219">
              a <code>uuid</code> is a valid universally unique identifier
              <a
                href="https://www.iso.org/standard/53416.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >ISO/IEC 9834-8:2008</a
              >, 32 hexadecimal (base-16) digits, displayed in five groups
              separated by hyphens, in the form 8-4-4-4-12.
            </li>
            <li data-sourcepos="64:1-64:149">
              a <code>string</code> literal is a <code>'single-quoted'</code> or
              <code>"double-quoted"</code> string; backslash <code>\</code> can
              be used as an escape character, see below for the details,
            </li>
            <li data-sourcepos="65:1-65:222">
              a <code>number</code> literal is a valid
              <a
                href="https://doi.org/10.1109%2FIEEESTD.2019.8766229"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >IEEE 754-2019</a
              >
              string representation of a number, such as e.g., the decimal point
              number <code>3.14</code> and the scientific notation number
              <code>1.23E45</code>,
            </li>
            <li data-sourcepos="66:1-66:303">
              a <code>datetime</code> literal consists of a prefix
              <code>D</code> and a valid
              <a
                href="https://www.iso.org/iso-8601-date-and-time-format.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >ISO 8601-1:2019</a
              >
              date and time with timezone, where time and timezone are optional
              parts, e.g.,
              <code>D2020-03-13T16:45:50.333</code>,
              <code>D2020-03-13T16:45+02:00</code>, <code>D20200313</code>,
              <code>D20200313164550.333</code>,
            </li>
            <li data-sourcepos="67:1-68:0">
              a <code>boolean</code> literal is either <code>true</code> or
              <code>false</code>.
            </li>
          </ul>
          <p>
            The literals are scopeless by default, i.e., they do not change the
            scope of the
            <a href="#expressions">expression</a>. However, it is supported to
            specify explicitly the scope of the literal if needed using a scope
            prefix, e.g., <code>l:'log-scoped string'</code>,
            <code>t:D2020-03-13T16:45+02.00</code>, and <code>e:3.14</code> are
            valid literals with the scopes specified. If an expression reduces
            to scopeless literals only, i.e., consists of no
            <a href="#attributes">attributes</a>, and no literal has a scope
            associated with, then the default scope of
            <code>event</code> applies.
          </p>
          <h3>Escape sequences in <code>string</code> literals</h3>
          <p>
            The <code>string</code> literals in PQL may contain escape
            sequences. An escape sequence consists of the backslash character
            <code>\</code> and the sequence of one or more characters. The
            complete list of available escape sequences is the table below.
          </p>
          <table>
            <thead>
              <tr data-sourcepos="74:1-74:37">
                <th data-sourcepos="74:2-74:20">Escape sequence</th>
                <th data-sourcepos="74:22-74:36">Meaning</th>
              </tr>
            </thead>
            <tbody>
              <tr data-sourcepos="76:1-76:37">
                <td data-sourcepos="76:2-76:20"><code>\b</code></td>
                <td data-sourcepos="76:22-76:36">Backspace</td>
              </tr>
              <tr data-sourcepos="77:1-77:37">
                <td data-sourcepos="77:2-77:20"><code>\n</code></td>
                <td data-sourcepos="77:22-77:36">New line</td>
              </tr>
              <tr data-sourcepos="78:1-78:37">
                <td data-sourcepos="78:2-78:20"><code>\t</code></td>
                <td data-sourcepos="78:22-78:36">Horizontal tab</td>
              </tr>
              <tr data-sourcepos="79:1-79:37">
                <td data-sourcepos="79:2-79:20"><code>\f</code></td>
                <td data-sourcepos="79:22-79:36">Form feed</td>
              </tr>
              <tr data-sourcepos="80:1-80:37">
                <td data-sourcepos="80:2-80:20"><code>\r</code></td>
                <td data-sourcepos="80:22-80:36">Carriage return</td>
              </tr>
              <tr data-sourcepos="81:1-81:88">
                <td data-sourcepos="81:2-81:20">
                  <code>\o</code>, <code>\oo</code>, <code>\ooo</code>
                </td>
                <td data-sourcepos="81:22-81:87">
                  Octal byte value, where <code>o</code> is a number from the
                  range from 0 to 7
                </td>
              </tr>
              <tr data-sourcepos="82:1-82:108">
                <td data-sourcepos="82:2-82:20"><code>\uxxxx</code></td>
                <td data-sourcepos="82:22-82:107">
                  16-bit Unicode character, where <code>x</code> is a
                  hexadecimal number from the range from 0 to F
                </td>
              </tr>
            </tbody>
          </table>
          <p>
            Any other character following the backslash
            <code>\</code> is read literally. To include a backslash character,
            write two backslashes <code>\\</code>; to include a single-quote in
            the single-quoted string use <code>\'</code>, and to include a
            double-quote in the double-quoted string use <code>\"</code>.
          </p>
          <p>
            It is the user's responsibility that the byte sequences created
            using the octal and Unicode escape sequences are valid characters in
            the UTF-8 encoding. The
            <code>\uxxxx</code> escape sequence can be used to specify UTF-16
            surrogate pairs to compose characters with code points larger than
            <code>\uFFFF</code>.
          </p>
          <h2 id="attributes">Attributes</h2>
          <p>
            The set of attributes available on every scope dynamically adapts to
            data: every attribute supplied by the data source is available for
            use. It is even possible that every log, trace, and event have
            different set of attributes associated with it.
          </p>
          <h3>The standard attributes</h3>
          <p>
            PQL defines <strong>standard attributes</strong> available at all
            times. A name of a standard attribute is a colon-separated list of
            the scope, the extension prefix, and the name of the attribute as
            defined in
            <a
              href="https://standards.ieee.org/standard/1849-2016.html"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >IEEE 1849-2016: IEEE Standard for eXtensible Event Stream (XES)
              for Achieving Interoperability in Event Logs and Event Streams</a
            >.
          </p>
          <p>A complete list of standard attributes is given below:</p>
          <ul>
            <li data-sourcepos="96:1-96:141">
              <code>log:concept:name</code> (type: <code>string</code>) - Stores
              a generally understood name for the log. Usually the name of the
              process having been executed.
            </li>
            <li data-sourcepos="97:1-97:74">
              <code>log:identity:id</code> (type: <code>uuid</code>) - Unique
              identifier (UUID) for the log.
            </li>
            <li data-sourcepos="98:1-98:434">
              <code>log:lifecycle:model</code> (type: <code>string</code>) -
              This attribute refers to the lifecycle transactional model used
              for all events in the log. If this attribute has a value of
              "standard", the standard lifecycle transactional model is assumed.
              If it is a value of "bpaf", the Business Process Analytics Format
              (BPAF) lifecycle transactional model is assumed. See
              <a
                href="https://standards.ieee.org/standard/1849-2016.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >IEEE 1849-2016</a
              >
              for details.
            </li>
            <li data-sourcepos="99:1-99:105">
              <code>log:xes:version</code> (type: <code>string</code>) - The
              version of the XES standard the log conforms to (e.g.,
              <code>1.0</code>).
            </li>
            <li data-sourcepos="100:1-100:197">
              <code>log:xes:features</code> (type: <code>string</code>) - A
              whitespace-separated list of optional XES features (e.g.,
              <code>nested-attributes</code>). If no optional features are used,
              this attribute shall have an empty value.
            </li>
            <li data-sourcepos="101:1-101:112">
              <code>trace:concept:name</code> (type: <code>string</code>) -
              Stores a generally understood name for the trace. Usually the case
              ID.
            </li>
            <li data-sourcepos="102:1-102:161">
              <code>trace:cost:currency</code> (type: <code>string</code>) - The
              currency (using the
              <a
                href="https://www.iso.org/standard/46121.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >ISO 4217:2008 standard</a
              >) of all costs of this trace.
            </li>
            <li data-sourcepos="103:1-103:145">
              <code>trace:cost:total</code> (type: <code>number</code>) - Total
              cost incurred for a trace. The value represents the sum of all the
              cost amounts within the element.
            </li>
            <li data-sourcepos="104:1-104:78">
              <code>trace:identity:id</code> (type: <code>uuid</code>) - Unique
              identifier (UUID) for the trace.
            </li>
            <li data-sourcepos="105:1-105:971">
              <code>trace:classifier:&lt;name&gt;</code> (type:
              <code>string</code>) - Refers the classifier for the
              <code>trace</code> scope using its log-specific
              <code>&lt;name&gt;</code>. The <code>&lt;name&gt;</code> must
              follow the regular expression
              <code>^[a-zA-Z\u0080-\u00FF_0-9]+$</code>. For the classifiers not
              compliant with this naming, the use of the square bracket syntax
              is required (see below). A classifier is a list of attributes,
              whose values give identity to a trace. See
              <a
                href="https://standards.ieee.org/standard/1849-2016.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >IEEE 1849-2016</a
              >
              for the details on classifiers. Note that different logs returned
              by the same query may define different classifiers, and so the
              definition of a specific classifier visible to traces from each
              log may be different. The use of this attribute is allowed only in
              the
              <a href="#the-select-clause"><code>select</code></a
              >, the <a href="#the-group-by-clause"><code>group by</code></a
              >, and the
              <a href="#the-order-by-clause"><code>order by</code></a>
              clauses, where it evaluates to a collection of attributes. It is
              strictly prohibited to use this attribute in other clauses.
            </li>
            <li data-sourcepos="106:1-106:158">
              <code>event:concept:name</code> (type: <code>string</code>) -
              Stores a generally understood name for an event. Usually the name
              of the executed activity represented by the event.
            </li>
            <li data-sourcepos="107:1-107:224">
              <code>event:concept:instance</code> (type: <code>string</code>) -
              This represents an identifier of the activity instance whose
              execution has generated the event. This way, multiple instances
              (occurrences) of the same activity can be told apart.
            </li>
            <li data-sourcepos="108:1-108:161">
              <code>event:cost:currency</code> (type: <code>string</code>) - The
              currency (using the
              <a
                href="https://www.iso.org/standard/46121.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >ISO 4217:2008 standard</a
              >) of all costs of this event.
            </li>
            <li data-sourcepos="109:1-109:146">
              <code>event:cost:total</code> (type: <code>number</code>) - Total
              cost incurred for an event. The value represents the sum of all
              the cost amounts within the element.
            </li>
            <li data-sourcepos="110:1-110:78">
              <code>event:identity:id</code> (type: <code>uuid</code>) - Unique
              identifier (UUID) for the event.
            </li>
            <li data-sourcepos="111:1-111:315">
              <code>event:lifecycle:transition</code> (type:
              <code>string</code>) - The transition attribute is defined for
              events, and specifies the lifecycle transition of each event. The
              transitions following the <em>standard</em> model should use this
              attribute. See
              <a
                href="https://standards.ieee.org/standard/1849-2016.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >IEEE 1849-2016</a
              >
              for the details.
            </li>
            <li data-sourcepos="112:1-112:295">
              <code>event:lifecycle:state</code> (type: <code>string</code>) -
              The state attribute is defined for events and specifies the
              lifecycle state of each event. The transitions following the
              <em>BPAF</em> model should use this attribute. See
              <a
                href="https://standards.ieee.org/standard/1849-2016.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >IEEE 1849-2016</a
              >
              for the details.
            </li>
            <li data-sourcepos="113:1-113:108">
              <code>event:org:resource</code> (type: <code>string</code>) - The
              name, or identifier, of the resource that triggered the event.
            </li>
            <li data-sourcepos="114:1-114:125">
              <code>event:org:role</code> (type: <code>string</code>) - The role
              of the resource that triggered the event, within the
              organizational structure.
            </li>
            <li data-sourcepos="115:1-115:145">
              <code>event:org:group</code> (type: <code>string</code>) - The
              group within the organizational structure, of which the resource
              that triggered the event is a member.
            </li>
            <li data-sourcepos="116:1-116:87">
              <code>event:time:timestamp</code> (type: <code>datetime</code>) -
              The UTC time at which the event occurred.
            </li>
            <li data-sourcepos="117:1-118:0">
              <code>event:classifier:&lt;name&gt;</code> (type:
              <code>string</code>) - Refers the classifier for the
              <code>event</code> scope using its log-specific
              <code>&lt;name&gt;</code>. The <code>&lt;name&gt;</code> must
              follow the regular expression
              <code>^[a-zA-Z\u0080-\u00FF_0-9]+$</code>. For the classifiers not
              compliant with this naming, the use of the square bracket syntax
              is required (see below). A classifier is a list of attributes,
              whose values give identity to an event. See
              <a
                href="https://standards.ieee.org/standard/1849-2016.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >IEEE 1849-2016</a
              >
              for the details on classifiers. Note that different logs returned
              by the same query may define different classifiers, and so the
              definition of a specific classifier visible to events from each
              log may be different. The use of this attribute is allowed only in
              the
              <a href="#the-select-clause"><code>select</code></a
              >, the <a href="#the-group-by-clause"><code>group by</code></a
              >, and the
              <a href="#the-order-by-clause"><code>order by</code></a>
              clauses, where it evaluates to a collection of attributes. It is
              strictly prohibited to use this attribute in other clauses.
            </li>
          </ul>
          <p>
            The attributes provided by the data source are translated to these
            standard names using XES extensions as defined in the
            <a
              href="https://standards.ieee.org/standard/1849-2016.html"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >IEEE 1849-2016 standard</a
            >, even if the name of the attribute differs in the data source. The
            translation works in three steps:
          </p>
          <ul>
            <li data-sourcepos="120:1-120:365">
              For each log in the data source separately read the log-specified
              prefixes for all standard XES extensions explicitly attached to
              this log. The standard XES extension is recognized by URI, e.g.,
              <code>http://www.xes-standard.org/concept.xesext</code>
              refers to the <code>concept</code> extension. Note that different
              logs may define different prefixes for the same standard
              attributes.
            </li>
            <li data-sourcepos="121:1-121:220">
              For each log in the data source separately, for each unattached
              standard XES extension attach it with its standard prefix as
              defined in the
              <a
                href="https://standards.ieee.org/standard/1849-2016.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >IEEE 1849-2016 standard</a
              >,
            </li>
            <li data-sourcepos="122:1-123:0">
              For each log in the data source separately, for each attribute in
              it change its prefix to the standard prefix as defined in the
              <a
                href="https://standards.ieee.org/standard/1849-2016.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >IEEE 1849-2016 standard</a
              >.
            </li>
          </ul>
          <p>
            Only the attributes from the XES extensions attached to the log are
            translated this way. The standard attributes that do not translate
            to any attribute in the log, are left
            <code>null</code>. Note that the attributes remain available under
            their original names when using the square bracket syntax (see
            below).
          </p>
          <p>
            Note that the attributes
            <code>trace:classifier:&lt;name&gt;</code> and
            <code>event:classifier:&lt;name&gt;</code> are extracted from the
            collection of XES classifiers provided by the data source with the
            log and their names do not follow that translation rule, as the
            <a
              href="https://standards.ieee.org/standard/1849-2016.html"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >IEEE 1849-2016 standard</a
            >
            does not define classifier prefixes. However, the list of attributes
            corresponding to the classifier follows translation accordingly.
          </p>
          <p>
            It is allowed to use <strong>shorthand names</strong> for the
            standard attributes by omitting the standard prefix of the defining
            XES extension and the following colon. E.g.,
            <code>event:concept:name</code> and <code>event:name</code> refer to
            the same attribute. Given the shorthand rules as defined for
            <a href="#scopes">scopes</a> above, <code>name</code>,
            <code>e:name</code>, and <code>e:concept:name</code> refer to
            <code>event:concept:name</code> too. This does not apply to
            <code>trace:classifier:&lt;name&gt;</code> and
            <code>event:classifier:&lt;name&gt;</code>, whose prefixes must not
            be omitted, but can be shortened to <code>c</code>, resulting in
            <code>trace:c:&lt;name&gt;</code> and
            <code>event:c:&lt;name&gt;</code>.
          </p>
          <h3>The bracket syntax for non-standard attributes</h3>
          <p>
            All other attributes are considered non-standard. The non-standard
            attributes are available using the square bracket syntax:
          </p>
          <ul>
            <li data-sourcepos="134:1-134:61">
              <code>[log:&lt;attribute&gt;]</code> - retrieves the
              <code>&lt;attribute&gt;</code> of a log,
            </li>
            <li data-sourcepos="135:1-135:65">
              <code>[trace:&lt;attribute&gt;]</code> - retrieves the
              <code>&lt;attribute&gt;</code> of a trace,
            </li>
            <li data-sourcepos="136:1-136:66">
              <code>[event:&lt;attribute&gt;]</code> - retrieves the
              <code>&lt;attribute&gt;</code> of an event,
            </li>
            <li data-sourcepos="137:1-137:119">
              <code>[trace:classifier:&lt;name&gt;]</code> - retrieves the trace
              classifier with <code>&lt;name&gt;</code>; see above for the
              details on classifiers,
            </li>
            <li data-sourcepos="138:1-139:0">
              <code>[event:classifier:&lt;name&gt;]</code> - retrieves the event
              classifier with <code>&lt;name&gt;</code>; see above for the
              details on classifiers.
            </li>
          </ul>
          <p>
            The square bracket syntax uses the untranslated fully-qualified
            names of the attributes as provided by the data source.
          </p>
          <p>
            The value of an attribute non-existent in a particular component
            referred to using the bracket syntax evaluates to
            <code>null</code> for this component. This behavior simplifies
            operations on components with different sets of attributes. Note
            that the
            <a
              href="https://standards.ieee.org/standard/1849-2016.html"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >IEEE 1849-2016 standard</a
            >
            allows for different sets of attributes for every event within the
            same trace, and for every trace within the same log.
          </p>
          <h1 class="headline">Identifiers</h1>
          <p>
            An identifier refers to the formal object representing a data item.
            A valid identifier is any of the following:
          </p>
          <ul>
            <li data-sourcepos="147:1-148:0">
              <a href="#attributes">Attribute name</a>.
            </li>
          </ul>
          <p>
            Note that the term <em>identifier</em>, although equivalent to the
            <em>attribute name</em>, is distinguished as a more general formal
            object reserved for future use.
          </p>
          <h1 id="functions" class="headline">Functions</h1>
          <p>
            PQL supports
            <a href="#aggregation-functions">aggregation</a> and
            <a href="#scalar-functions">scalar</a> functions of attributes,
            literals, and an expression thereof. The functions in PQL do not
            cause side effects.
          </p>
          <h2 id="aggregation-functions">Aggregation functions</h2>
          <p>
            PQL supports aggregation functions. For a query with the
            <a href="#the-group-by-clause"><code>group by</code></a>
            clause, an aggregation function yields a single value for each group
            on each scope. For a query without the
            <code>group by</code> clause, use of the aggregation function
            triggers the
            <a href="#implicit-group-by">implicit group by clause</a>.
            Aggregation functions can be used in the
            <a href="#the-select-clause"><code>select</code></a> and
            <a href="#the-order-by-clause"><code>order by</code></a>
            clauses. They cannot appear in the
            <a href="#the-where-clause"><code>where</code></a
            >, the <a href="#the-group-by-clause"><code>group by</code></a
            >, the <a href="#the-limit-clause"><code>limit</code></a
            >, and the
            <a href="#the-offset-clause"><code>offset</code></a>
            clauses.
          </p>
          <p>The complete list of aggregation functions is as follows:</p>
          <ul>
            <li data-sourcepos="161:1-161:215">
              <code>min(any) -&gt; any</code> - Returns the minimum non-null
              value in the set of values of the given attribute for a group, or
              <code>null</code> if such value does not exit. See
              <a href="#comparison-operators">comparison operators</a>
              for details.
            </li>
            <li data-sourcepos="162:1-162:215">
              <code>max(any) -&gt; any</code> - Returns the maximum non-null
              value in the set of values of the given attribute for a group, or
              <code>null</code> if such value does not exit. See
              <a href="#comparison-operators">comparison operators</a>
              for details.
            </li>
            <li data-sourcepos="163:1-163:97">
              <code>avg(number) -&gt; number</code> - Returns the average of all
              values of the given attribute for a group.
            </li>
            <li data-sourcepos="164:1-164:99">
              <code>count(any) -&gt; number</code> - Returns the count of
              non-null values of the given attribute for a group.
            </li>
            <li data-sourcepos="165:1-166:0">
              <code>sum(number) -&gt; number</code> - Returns the sum of all
              values of the given attribute for a group.
            </li>
          </ul>
          <p>
            Aggregation function can only take an identifier as an argument.
          </p>
          <h2 id="scalar-functions">Scalar functions</h2>
          <p>
            A scalar function takes zero or more scalar arguments and yields a
            new value.
          </p>
          <p>The complete list of scalar functions is as follows:</p>
          <ul>
            <li data-sourcepos="174:1-174:123">
              <code>date(datetime) -&gt; datetime</code> - Returns the date part
              of a datetime (hours, minutes, seconds and milliseconds are
              zeroed).
            </li>
            <li data-sourcepos="175:1-175:98">
              <code>time(datetime) -&gt; datetime</code> - Return the time part
              of a datetime (year, month, day are zeroed).
            </li>
            <li data-sourcepos="176:1-176:64">
              <code>year(datetime) -&gt; number</code> - Returns the year from a
              datetime.
            </li>
            <li data-sourcepos="177:1-177:73">
              <code>month(datetime) -&gt; number</code> - Returns the month
              (1-12) from a datetime.
            </li>
            <li data-sourcepos="178:1-178:78">
              <code>day(datetime) -&gt; number</code> - Returns the day of month
              (1-31) from a datetime.
            </li>
            <li data-sourcepos="179:1-179:77">
              <code>hour(datetime) -&gt; number</code> - Returns the hour value
              (0-23) from a datetime.
            </li>
            <li data-sourcepos="180:1-180:81">
              <code>minute(datetime) -&gt; number</code> - Returns the minute
              value (0-59) from a datetime.
            </li>
            <li data-sourcepos="181:1-181:81">
              <code>second(datetime) -&gt; number</code> - Returns the second
              value (0-59) from a datetime.
            </li>
            <li data-sourcepos="182:1-182:92">
              <code>millisecond(datetime) -&gt; number</code> - Returns the
              millisecond value (0-999) from a datetime.
            </li>
            <li data-sourcepos="183:1-183:76">
              <code>quarter(datetime) -&gt; number</code> - Returns the quarter
              (1-4) from a datetime.
            </li>
            <li data-sourcepos="184:1-184:123">
              <code>dayofweek(datetime) -&gt; number</code> - Returns the day of
              week from a datetime. 1 for Sunday, 2 for Monday, 3 for Tuesday
              etc.
            </li>
            <li data-sourcepos="185:1-185:161">
              <code>now() -&gt; datetime</code> - Returns the datetime of invoke
              of the query; successive calls to this function in the same query
              are guaranteed to return the same value.
            </li>
            <li data-sourcepos="186:1-186:79">
              <code>upper(string) -&gt; string</code> - Converts the given
              string converted to uppercase.
            </li>
            <li data-sourcepos="187:1-187:79">
              <code>lower(string) -&gt; string</code> - Converts the given
              string converted to lowercase.
            </li>
            <li data-sourcepos="188:1-189:0">
              <code>round(number) -&gt; number</code> - Rounds the given number
              to the nearest integer value; rounds half away from zero.
            </li>
          </ul>
          <h1 id="expressions" class="headline">Expressions</h1>
          <h2 id="arithmetic-expressions">Arithmetic expressions</h2>
          <p>
            PQL defines arithmetic expressions involving addition
            <code>+</code>, subtraction <code>-</code>, multiplication
            <code>*</code>, and division <code>/</code>
            <a href="#operators">operators</a>. It allows for overwriting
            standard <a href="#operator-precedence">precedence rules</a> using
            the round brackets <code>()</code>. An arithmetic expression may use
            a valid identifier, a function call, a scalar value, and any
            combination thereof.
          </p>
          <p>
            E.g., <code>e:cost:total</code>,
            <code>e:cost:total * [e:currency-rate:EURtoUSD]</code>,
            <code>avg(e:cost:total)</code>, and
            <code>(e:cost:total + 10) * [e:currency-rate:EURtoUSD]</code>
            are valid arithmetic expressions.
          </p>
          <h2 id="logical-expression">Logical expressions</h2>
          <p>
            PQL defines logic expressions involving <code>and</code>,
            <code>or</code>, and <code>not</code>
            <a href="#operators">operators</a>. It allows for overwriting
            standard <a href="#operator-precedence">precedence rules</a> using
            the round brackets <code>()</code>. A logical expression may use
            <code>boolean</code> attributes, literals <code>true</code> and
            <code>false</code>, comparisons of arithmetic expressions, and a
            combination thereof.
          </p>
          <p>
            E.g., <code>e:org:resource = 'scott'</code>,
            <code>e:org:resource = 'scott' or e:org:group = 'helpdesk'</code>,
            and
            <code
              >(e:org:resource = 'scott' or e:org:group = 'helpdesk') and
              dayofweek(e:time:timestamp) = 1</code
            >
            are valid logic expressions.
          </p>
          <h2>Properties of an expression</h2>
          <p>
            Every expression is characterized by the scope. The scope of the
            expression is calculated in three steps:
          </p>
          <ul>
            <li data-sourcepos="203:1-203:128">
              Collect the set of the scopes with the optional hoisting prefix of
              all attributes, literals, and functions in this expression,
            </li>
            <li data-sourcepos="204:1-204:81">
              Hoist the scopes in this set using the associated hoisting prefix
              wherever set,
            </li>
            <li data-sourcepos="205:1-206:0">
              Select the lowest scope from this set or the
              <code>event</code> scope if empty.
            </li>
          </ul>
          <h1 id="operators" class="headline">Operators</h1>
          <p>This section summarizes operators available in PQL.</p>
          <h2>Arithmetic operators</h2>
          <ul>
            <li data-sourcepos="212:1-212:62">
              <code>number * number -&gt; number</code> - multiplication of two
              numbers.
            </li>
            <li data-sourcepos="213:1-213:62">
              <code>number / number -&gt; number</code> - multiplication of two
              numbers.
            </li>
            <li data-sourcepos="214:1-214:56">
              <code>number + number -&gt; number</code> - addition of two
              numbers.
            </li>
            <li data-sourcepos="215:1-216:0">
              <code>number - number -&gt; number</code> - subtraction of two
              numbers.
            </li>
          </ul>
          <p>
            All arithmetic operators yield <code>null</code> if any of their
            operands is <code>null</code>.
          </p>
          <h2>Text operators</h2>
          <ul>
            <li data-sourcepos="221:1-221:61">
              <code>string + string -&gt; string</code> - concatenation of two
              strings.
            </li>
            <li data-sourcepos="222:1-225:217">
              <code>string like string -&gt; boolean</code> - evaluates to
              <code>true</code> if and only if the first string matches from the
              beginning to the end the pattern specified by the second string,
              <code>false</code> otherwise. The pattern consists of any
              characters interleaved with zero or more placeholder characters.
              The following placeholder characters are defined:
              <ul data-sourcepos="223:3-225:217">
                <li data-sourcepos="223:3-223:44">
                  <code>_</code> - matches exactly one any character,
                </li>
                <li data-sourcepos="224:3-225:217">
                  <code>%</code> - matches zero or more characters. To match a
                  literal <code>_</code> or <code>%</code> without matching
                  other characters, the respective character in the pattern must
                  be preceded by the escape character <code>\</code>. To match
                  the escape character itself, write two escape characters.
                </li>
              </ul>
            </li>
            <li data-sourcepos="226:1-227:0">
              <code>string matches string -&gt; boolean</code> - evaluates to
              <code>true</code> if and only if the first string matches the
              regular expression specified by the second string,
              <code>false</code> otherwise. The match may occur anywhere within
              the string unless the regular expression is explicitly anchored to
              the beginning or the end of the string. The regular expression
              must conform to the
              <a
                href="https://standards.ieee.org/content/ieee-standards/en/standard/1003_1-2017.html"
                rel="nofollow noreferrer noopener"
                target="_blank"
                >IEEE 1003-1:2017 Standard for Information Technology--Portable
                Operating System Interface (POSIX(R)) Base Specifications</a
              >.
            </li>
          </ul>
          <h2>Temporal operators</h2>
          <ul>
            <li data-sourcepos="230:1-231:0">
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
          <h2>Logic operators</h2>
          <ul>
            <li data-sourcepos="236:1-236:57">
              <code>boolean and boolean -&gt; boolean</code> - Boolean
              conjunction.
            </li>
            <li data-sourcepos="237:1-237:56">
              <code>boolean or boolean -&gt; boolean</code> - Boolean
              disjunction.
            </li>
            <li data-sourcepos="238:1-239:0">
              <code>not boolean -&gt; boolean</code> - Boolean negation.
            </li>
          </ul>
          <p>
            All logic operators yield <code>null</code> if any of their operands
            is <code>null</code>.
          </p>
          <h2 id="comparison-operators">Comparison operators</h2>
          <ul>
            <li data-sourcepos="244:1-244:122">
              <code>any = any -&gt; boolean</code> - Evaluates to
              <code>true</code> if and only if both values have the same type
              and value, <code>false</code> otherwise.
            </li>
            <li data-sourcepos="245:1-245:123">
              <code>any != any -&gt; boolean</code> - Evaluates to
              <code>false</code> if and only if both values have the same type
              and value, <code>true</code> otherwise.
            </li>
            <li data-sourcepos="246:1-246:166">
              <code>any &lt; any -&gt; boolean</code> - Evaluates to
              <code>true</code> if and only if both arguments have the same type
              and the first argument is smaller than the second,
              <code>false</code> otherwise.
            </li>
            <li data-sourcepos="247:1-247:178">
              <code>any &lt;= any -&gt; boolean</code> - Evaluates to
              <code>true</code> if and only if both arguments have the same type
              and the first argument is smaller than or equal to the second,
              <code>false</code> otherwise.
            </li>
            <li data-sourcepos="248:1-248:165">
              <code>any &gt; any -&gt; boolean</code> - Evaluates to
              <code>true</code> if and only if both arguments have the same type
              and the first argument is larger than the second,
              <code>false</code> otherwise.
            </li>
            <li data-sourcepos="249:1-250:0">
              <code>any &gt;= any -&gt; boolean</code> - Evaluates to
              <code>true</code> if and only if both arguments have the same type
              and the first argument is larger than or equal to the second,
              <code>false</code> otherwise.
            </li>
          </ul>
          <p>
            For these operators, <code>false</code> is considered smaller than
            <code>true</code>, numbers are compared using the
            <a
              href="https://doi.org/10.1109%2FIEEESTD.2019.8766229"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >IEEE 754-2019</a
            >
            rules, <code>datetime</code>s are compared with earlier being
            smaller, strings are compared alphabetically, with case-sensitivity.
            Comparison to <code>null</code> yields <code>false</code>.
          </p>
          <h2>Type-independent operators</h2>
          <ul>
            <li data-sourcepos="255:1-255:99">
              <code>any is null -&gt; boolean</code> - Evaluates to
              <code>true</code> if and only if <code>any</code> is
              <code>null</code>, <code>false</code> otherwise.
            </li>
            <li data-sourcepos="256:1-256:107">
              <code>any is not null -&gt; boolean</code> - Evaluates to
              <code>true</code> if and only if <code>any</code> is not
              <code>null</code>, <code>false</code> otherwise.
            </li>
            <li data-sourcepos="257:1-257:174">
              <code>any in (any, any,..., any) -&gt; boolean</code> - Evaluates
              to <code>true</code> if and only if the first
              <code>any</code>
              <a href="#comparison-operators">equals</a> any of the remaining
              values, <code>false</code> otherwise.
            </li>
            <li data-sourcepos="258:1-260:0">
              <code>any not in (any, any,..., any) -&gt; boolean</code>
              - Evaluates to <code>false</code> if and only if the first
              <code>any</code>
              <a href="#comparison-operators">equals</a> any of the remaining
              values, <code>true</code> otherwise.
            </li>
          </ul>
          <h2 id="operator-precedence">Operator precedence</h2>
          <p>This table orders the operators in the descending precedence:</p>
          <table>
            <thead>
              <tr data-sourcepos="264:1-264:60">
                <th data-sourcepos="264:2-264:11">Precedence</th>
                <th data-sourcepos="264:13-264:45">Operator</th>
                <th data-sourcepos="264:47-264:59">Associativity</th>
              </tr>
            </thead>
            <tbody>
              <tr data-sourcepos="266:1-266:60">
                <td data-sourcepos="266:2-266:11">1</td>
                <td data-sourcepos="266:13-266:45">
                  <code>*</code>, <code>/</code>
                </td>
                <td data-sourcepos="266:47-266:59">left</td>
              </tr>
              <tr data-sourcepos="267:1-267:60">
                <td data-sourcepos="267:2-267:11">2</td>
                <td data-sourcepos="267:13-267:45">
                  <code>+</code>, <code>-</code>
                </td>
                <td data-sourcepos="267:47-267:59">left</td>
              </tr>
              <tr data-sourcepos="268:1-268:60">
                <td data-sourcepos="268:2-268:11">3</td>
                <td data-sourcepos="268:13-268:45">
                  <code>in</code>, <code>not in</code>, <code>like</code>,
                  <code>matches</code>
                </td>
                <td data-sourcepos="268:47-268:59">none</td>
              </tr>
              <tr data-sourcepos="269:1-269:60">
                <td data-sourcepos="269:2-269:11">4</td>
                <td data-sourcepos="269:13-269:45">
                  <code>=</code>, <code>!=</code>, <code>&lt;</code> ,
                  <code>&lt;=</code>, <code>&gt;</code>,
                  <code>&gt;=</code>
                </td>
                <td data-sourcepos="269:47-269:59">none</td>
              </tr>
              <tr data-sourcepos="270:1-270:60">
                <td data-sourcepos="270:2-270:11">5</td>
                <td data-sourcepos="270:13-270:45">
                  <code>is null</code>, <code>is not null</code>
                </td>
                <td data-sourcepos="270:47-270:59">none</td>
              </tr>
              <tr data-sourcepos="271:1-271:60">
                <td data-sourcepos="271:2-271:11">6</td>
                <td data-sourcepos="271:13-271:45"><code>not</code></td>
                <td data-sourcepos="271:47-271:59">right</td>
              </tr>
              <tr data-sourcepos="272:1-272:60">
                <td data-sourcepos="272:2-272:11">7</td>
                <td data-sourcepos="272:13-272:45"><code>and</code></td>
                <td data-sourcepos="272:47-272:59">left</td>
              </tr>
              <tr data-sourcepos="273:1-273:60">
                <td data-sourcepos="273:2-273:11">8</td>
                <td data-sourcepos="273:13-273:45"><code>or</code></td>
                <td data-sourcepos="273:47-273:59">left</td>
              </tr>
            </tbody>
          </table>
          <h1 class="headline">Syntax</h1>
          <p>
            A PQL query is a list of the below clauses in the order specified
            below. All clauses are optional. An empty query is a valid query and
            returns all data in the data source.
          </p>
          <ul>
            <li data-sourcepos="279:1-279:32">
              <a href="#the-select-clause"><code>select</code></a>
            </li>
            <li data-sourcepos="280:1-280:31">
              <a href="#the-where-clause"><code>where</code></a>
            </li>
            <li data-sourcepos="281:1-281:37">
              <a href="#the-group-by-clause"><code>group by</code></a>
            </li>
            <li data-sourcepos="282:1-282:36">
              <a href="#the-order-by-clause"><code>order by</code></a>
            </li>
            <li data-sourcepos="283:1-283:30">
              <a href="#the-limit-clause"><code>limit</code></a>
            </li>
            <li data-sourcepos="284:1-285:0">
              <a href="#the-offset-clause"><code>offset</code></a>
            </li>
          </ul>
          <h2 id="the-select-clause">The select clause</h2>
          <p>
            The select clause specifies the attributes to fetch. The select
            clause takes one of the forms below:
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">select</span> <span class="o">*</span></span>
<span id="LC2" class="line" lang="sql"><span class="k">select</span> <span class="o">&lt;</span><span class="n">scope1</span><span class="o">&gt;</span><span class="p">:</span><span class="o">*</span><span class="p">[,</span> <span class="o">&lt;</span><span class="n">scope2</span><span class="o">&gt;</span><span class="p">:</span><span class="o">*</span><span class="p">[,</span> <span class="o">&lt;</span><span class="n">scope3</span><span class="o">&gt;</span><span class="p">:</span><span class="o">*</span><span class="p">]]</span></span>
<span id="LC3" class="line" lang="sql"><span class="k">select</span> <span class="o">&lt;</span><span class="n">expression1</span><span class="o">&gt;</span><span class="p">[,</span> <span class="o">&lt;</span><span class="n">expression2</span><span class="o">&gt;</span><span class="p">[,</span> <span class="p">...[,</span> <span class="o">&lt;</span><span class="n">expressionN</span><span class="o">&gt;</span><span class="p">]]]</span></span></code></pre>
          <p>
            Where the first form selects all available attributes from the data
            model. This is equivalent to not specifying the
            <code>select</code> clause at all. However, for efficient evaluation
            of complex queries on large data sources, it is recommended to
            specify attributes explicitly using the third form.
          </p>
          <p>
            The second form selects all attributes on the given scopes. The
            third form enables us to select specific
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
            <a
              href="https://standards.ieee.org/standard/1849-2016.html"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >IEEE 1849-2016 standard</a
            >
            forbids duplicate attribute names in the same component, except for
            the list attribute. PQL follows this restriction and each time a
            query selects the same attribute twice, e.g., by direct reference
            and using a classifier, only the first reference is retrieved.
          </p>
          <p>
            E.g., the below query selects the
            <code>concept:name</code> attribute for the <code>log</code> scope,
            the <code>concept:name</code> and
            <code>cost:currency</code> attributes for the
            <code>trace</code> scope, and the <code>concept:name</code> and
            <code>cost:total</code> attributes for the <code>event</code> scope.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">select</span> <span class="n">l</span><span class="p">:</span><span class="n">name</span><span class="p">,</span> <span class="n">t</span><span class="p">:</span><span class="n">name</span><span class="p">,</span> <span class="n">t</span><span class="p">:</span><span class="n">currency</span><span class="p">,</span> <span class="n">e</span><span class="p">:</span><span class="n">name</span><span class="p">,</span> <span class="n">e</span><span class="p">:</span><span class="n">total</span></span></code></pre>
          <p>
            The below query selects the
            <code>concept:name</code> attribute for the
            <code>trace</code> scope, and all attributes for the
            <code>event</code> scope.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">select</span> <span class="n">t</span><span class="p">:</span><span class="n">name</span><span class="p">,</span> <span class="n">e</span><span class="p">:</span><span class="o">*</span></span></code></pre>
          <p>
            The below query selects the attributes defined in the 'businesscase'
            classifier for the <code>trace</code> scope and defined in the
            <code>activity_resource</code> classifier for the
            <code>event</code> scope.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">select</span> <span class="n">t</span><span class="p">:</span><span class="n">classifier</span><span class="p">:</span><span class="n">businesscase</span><span class="p">,</span> <span class="n">e</span><span class="p">:</span><span class="n">classifier</span><span class="p">:</span><span class="n">activity_resource</span></span></code></pre>
          <p>
            The below query selects the minimum, the average, and the maximum of
            the total cost for all traces in the data source.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">select</span> <span class="k">min</span><span class="p">(</span><span class="n">t</span><span class="p">:</span><span class="n">total</span><span class="p">),</span> <span class="k">avg</span><span class="p">(</span><span class="n">t</span><span class="p">:</span><span class="n">total</span><span class="p">),</span> <span class="k">max</span><span class="p">(</span><span class="n">t</span><span class="p">:</span><span class="n">total</span><span class="p">)</span></span></code></pre>
          <h2 id="the-where-clause">The where clause</h2>
          <p>
            The <code>where</code> clause <em>filters</em> components to be
            fetched by the PQL query. It takes a form of
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">where</span> <span class="o">&lt;</span><span class="n">logical_expression</span><span class="o">&gt;</span></span></code></pre>
          <p>
            Where <code>&lt;logical_expression&gt;</code> refers to an arbitrary
            <a href="#logical-expression">logical expression</a>.
          </p>
          <p>
            E.g., the below query fetches the logs with traces with events at
            weekends. The logs and traces without weekend events are filtered
            out. The events in workdays are filtered out.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">where</span> <span class="n">dayofweek</span><span class="p">(</span><span class="n">e</span><span class="p">:</span><span class="nb">timestamp</span><span class="p">)</span> <span class="k">in</span> <span class="p">(</span><span class="mi">1</span><span class="p">,</span> <span class="mi">7</span><span class="p">)</span></span></code></pre>
          <p>
            In contrast, the below query fetches the logs with traces with
            events at weekends, however, it keeps events in other days thanks to
            <a href="#scope-hoisting">scope hoisting</a>. The logs and traces
            without events at weekends are filtered out.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">where</span> <span class="n">dayofweek</span><span class="p">(</span><span class="o">^</span><span class="n">e</span><span class="p">:</span><span class="nb">timestamp</span><span class="p">)</span> <span class="k">in</span> <span class="p">(</span><span class="mi">1</span><span class="p">,</span> <span class="mi">7</span><span class="p">)</span></span></code></pre>
          <p>
            The next query fetches the logs with traces with events at weekends.
            The logs without weekend events are filtered out, however, the
            traces without weekend events but in the logs containing the weekend
            events are returned.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">where</span> <span class="n">dayofweek</span><span class="p">(</span><span class="o">^^</span><span class="n">e</span><span class="p">:</span><span class="nb">timestamp</span><span class="p">)</span> <span class="k">in</span> <span class="p">(</span><span class="mi">1</span><span class="p">,</span> <span class="mi">7</span><span class="p">)</span></span></code></pre>
          <p>
            The below query selects the logs with traces having the currency of
            their total cost not reported as the currency of their children's
            events. All events are kept.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">where</span> <span class="k">not</span><span class="p">(</span><span class="n">t</span><span class="p">:</span><span class="n">currency</span> <span class="o">=</span> <span class="o">^</span><span class="n">e</span><span class="p">:</span><span class="n">currency</span><span class="p">)</span></span></code></pre>
          <p>
            In contrast, the below query filters out the events having the same
            currency as their corresponding traces.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">where</span> <span class="n">t</span><span class="p">:</span><span class="n">currency</span> <span class="o">!=</span> <span class="n">e</span><span class="p">:</span><span class="n">currency</span></span></code></pre>
          <p>
            The below query fetches the logs with traces having the currency of
            their total cost not reported as the currency of their children
            events and the total cost of the trace is
            <code>null</code>.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">where</span> <span class="k">not</span><span class="p">(</span><span class="n">t</span><span class="p">:</span><span class="n">currency</span> <span class="o">=</span> <span class="o">^</span><span class="n">e</span><span class="p">:</span><span class="n">currency</span><span class="p">)</span> <span class="k">and</span> <span class="n">t</span><span class="p">:</span><span class="n">total</span> <span class="k">is</span> <span class="k">null</span></span></code></pre>
          <h2 id="the-group-by-clause">The group by clause</h2>
          <p>
            The <code>group by</code> clause groups components based on the
            values of the given attributes. It is possible to specify one or
            more attributes using the below syntax. If the attribute is a
            classifier, i.e., <code>trace:classifier:&lt;name&gt;</code> or
            <code>event:classifier:&lt;name&gt;</code>, then this attribute
            expands to a list of actual attributes.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">group</span> <span class="k">by</span> <span class="o">&lt;</span><span class="n">attribute1</span><span class="o">&gt;</span><span class="p">[,</span> <span class="o">&lt;</span><span class="n">attribute2</span><span class="o">&gt;</span><span class="p">[,...[,</span> <span class="o">&lt;</span><span class="n">attribute3</span><span class="o">&gt;</span><span class="p">]]]</span></span></code></pre>
          <p>
            The scope of the attribute corresponds to the scope of grouping.
            <a href="#scope-hoisting">Scope hoisting</a> is supported and allows
            for grouping of the parent-scope components using the ordered list
            of the values of the child-scope attributes. E.g., the hoisted
            attribute <code>^event:concept:name</code> allows for grouping of
            traces into process variants based on the ordered list of the values
            of the <code>concept:name</code> attribute of the underlying events.
            As a result, each variant corresponds to the group of traces having
            the same sequence of events.
          </p>
          <p>
            The <code>group by</code> clause with a scope S restricts the
            attributes available in the <code>select</code> and
            <code>order by</code> clauses on scope S and children scopes to the
            attributes specified in this clause. All other attributes may be
            used as arguments for aggregation functions.
          </p>
          <p>
            Note that the query without the
            <a href="#the-select-clause"><code>select</code></a> clause fetches
            all <em>available</em> attributes rather than all attributes. For
            the queries with the <code>group by</code> clause this means that
            for the grouped scope and the lower scopes only the attributes
            enumerated in the <code>group by</code> clause are fetched.
          </p>
          <p>
            E.g., the below query selects all logs in the data source with all
            their attributes, groups the traces into variants using the
            <a href="#attributes">classifier</a>
            <code>event:classifier:activity</code>, and for each trace variant
            selects all events with the attributes listed in the classifier
            definition.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">group</span> <span class="k">by</span> <span class="o">^</span><span class="n">e</span><span class="p">:</span><span class="n">classifier</span><span class="p">:</span><span class="n">activity</span></span></code></pre>
          <p>
            The below query selects <code>trace:concept:name</code> for each
            trace, and the sum of total costs for each group of events having
            the same <code>event:concept:name</code> within each trace
            individually.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">select</span> <span class="n">t</span><span class="p">:</span><span class="n">name</span><span class="p">,</span> <span class="n">e</span><span class="p">:</span><span class="n">name</span><span class="p">,</span> <span class="k">sum</span><span class="p">(</span><span class="n">e</span><span class="p">:</span><span class="n">total</span><span class="p">)</span></span>
<span id="LC2" class="line" lang="sql"><span class="k">group</span> <span class="k">by</span> <span class="n">e</span><span class="p">:</span><span class="n">name</span></span></code></pre>
          <p>
            The below query seeks for the variants of the traces with the same
            sequence of events, comparing the events using the
            <code>event:concept:name</code> attribute. The events in the
            resulting trace variants contain the
            <code>event:concept:name</code> attribute and the sum of the total
            costs incurred by all events with the same position within the
            variant.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">select</span> <span class="n">e</span><span class="p">:</span><span class="n">name</span><span class="p">,</span> <span class="k">sum</span><span class="p">(</span><span class="n">e</span><span class="p">:</span><span class="n">total</span><span class="p">)</span></span>
<span id="LC2" class="line" lang="sql"><span class="k">group</span> <span class="k">by</span> <span class="o">^</span><span class="n">e</span><span class="p">:</span><span class="n">name</span><span class="p">,</span> <span class="n">e</span><span class="p">:</span><span class="n">name</span></span></code></pre>
          <p>
            The below query for each group of logs with the same sequence of
            events among all traces, comparing events using the
            <code>event:concept:name</code> attribute, selects the aggregated
            log. In the resulting log, the events contain the
            <code>event:concept:name</code> attribute and the sum of the total
            costs incurred by all events with the same position within this
            group of logs.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">select</span> <span class="n">e</span><span class="p">:</span><span class="n">name</span><span class="p">,</span> <span class="k">sum</span><span class="p">(</span><span class="n">e</span><span class="p">:</span><span class="n">total</span><span class="p">)</span></span>
<span id="LC2" class="line" lang="sql"><span class="k">group</span> <span class="k">by</span> <span class="o">^^</span><span class="n">e</span><span class="p">:</span><span class="n">name</span><span class="p">,</span> <span class="n">e</span><span class="p">:</span><span class="n">name</span></span></code></pre>
          <h3 id="implicit-group-by">Implicit group by</h3>
          <p>
            The use of the
            <a href="#aggregation-functions">aggregation function</a> in the
            <a href="#the-select-clause"><code>select</code></a> or
            <a href="#the-order-by-clause"><code>order by</code></a>
            clause without the use of any attribute of the same scope as this
            aggregation function in the
            <code>group by</code> clause (or omitting the
            <code>group by</code> clause) implies an implicit
            <code>group by</code> clause for this scope. The implicit
            <code>group by</code> groups all components on this scope into a
            single aggregation component carrying the aggregated data for all
            matching components. The query with the implicit
            <code>group by</code> clause on scope S must aggregate all
            attributes on scope s referenced in the <code>select</code> and
            <code>order by</code> clauses. It is strictly forbidden to use an
            unaggregated attribute together with the implicit
            <code>group by</code> clause.
          </p>
          <p>
            E.g., the below query for each log and for each trace yields a
            single event holding the average total cost and the boundaries of
            the time window of all events in the corresponding trace.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">select</span> <span class="k">avg</span><span class="p">(</span><span class="n">e</span><span class="p">:</span><span class="n">total</span><span class="p">),</span> <span class="k">min</span><span class="p">(</span><span class="n">e</span><span class="p">:</span><span class="nb">timestamp</span><span class="p">),</span> <span class="k">max</span><span class="p">(</span><span class="n">e</span><span class="p">:</span><span class="nb">timestamp</span><span class="p">)</span></span></code></pre>
          <p>
            The below query yields a single log holding the average cost
            incurred by and the boundaries of the time window of all events
            belonging to all logs in the data source.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">select</span> <span class="k">avg</span><span class="p">(</span><span class="o">^^</span><span class="n">e</span><span class="p">:</span><span class="n">total</span><span class="p">),</span> <span class="k">min</span><span class="p">(</span><span class="o">^^</span><span class="n">e</span><span class="p">:</span><span class="nb">timestamp</span><span class="p">),</span> <span class="k">max</span><span class="p">(</span><span class="o">^^</span><span class="n">e</span><span class="p">:</span><span class="nb">timestamp</span><span class="p">)</span></span></code></pre>
          <h2 id="the-order-by-clause">The order by clause</h2>
          <p>
            The <code>order by</code> clause specifies the sorting order of the
            results. Its syntax is as follows:
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">order</span> <span class="k">by</span> <span class="o">&lt;</span><span class="n">expression1</span><span class="o">&gt;</span> <span class="p">[</span><span class="o">&lt;</span><span class="n">direction</span><span class="o">&gt;</span><span class="p">][,</span> <span class="o">&lt;</span><span class="n">expression2</span><span class="o">&gt;</span> <span class="p">[</span><span class="o">&lt;</span><span class="n">direction</span><span class="o">&gt;</span><span class="p">][,...[,</span> <span class="o">&lt;</span><span class="n">expressionN</span><span class="o">&gt;</span> <span class="p">[</span><span class="o">&lt;</span><span class="n">direction</span><span class="o">&gt;</span><span class="p">]]]]</span></span></code></pre>
          <p>
            where <code>&lt;expression*&gt;</code> placeholders refer to
            <a href="#arithmetic-expressions">arithmetic expressions</a>, whose
            values are ordering keys. The <code>trace:classifier:*</code> and
            <code>event:classifier:*</code> attributes expand to the list of
            underlying attributes. The
            <code>&lt;direction&gt;</code> placeholders refer to the ordering
            direction, either <code>asc</code> or <code>desc</code> for
            ascending or descending direction, respectively. When omitted,
            <code>asc</code> is assumed.
          </p>
          <p>
            The ordering is the same as imposed by the
            <a href="#comparison-operators">comparison operators</a>, except
            that the <code>null</code> values are considered greater than all
            other values.
          </p>
          <p>
            By omitting the <code>order by</code> clause, the components are
            returned in the same order as provided by the data source. E.g., the
            order of the traces and events in the XES file.
          </p>
          <p>
            E.q., the below query orders the events within a trace ascendingly
            by their timestamps.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">order</span> <span class="k">by</span> <span class="n">e</span><span class="p">:</span><span class="nb">timestamp</span></span></code></pre>
          <p>
            The below query orders the traces descendingly by their total costs
            and the events within each trace ascendingly by their timestamps.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">order</span> <span class="k">by</span> <span class="n">t</span><span class="p">:</span><span class="n">total</span> <span class="k">desc</span><span class="p">,</span> <span class="n">e</span><span class="p">:</span><span class="nb">timestamp</span></span></code></pre>
          <h2 id="the-limit-clause">The limit clause</h2>
          <p>
            The limit clause imposes the limit on the number of the returned
            components. It takes the form of:
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">limit</span> <span class="o">&lt;</span><span class="k">scope</span><span class="o">&gt;</span><span class="p">:</span><span class="o">&lt;</span><span class="n">number</span><span class="o">&gt;</span><span class="p">[,</span> <span class="o">&lt;</span><span class="k">scope</span><span class="o">&gt;</span><span class="p">:</span><span class="o">&lt;</span><span class="n">number</span><span class="o">&gt;</span><span class="p">[,</span> <span class="o">&lt;</span><span class="k">scope</span><span class="o">&gt;</span><span class="p">:</span><span class="o">&lt;</span><span class="n">number</span><span class="o">&gt;</span><span class="p">]]</span></span></code></pre>
          <p>
            where the <code>&lt;scope&gt;</code> placeholder refers to the scope
            on which to impose the limit given by the corresponding
            <code>&lt;number&gt;</code> placeholder.
          </p>
          <p>
            E.g., the below query returns at most five logs, at most ten traces
            per log, and at most twenty events per trace.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">limit</span> <span class="n">l</span><span class="p">:</span><span class="mi">5</span><span class="p">,</span> <span class="n">t</span><span class="p">:</span><span class="mi">10</span><span class="p">,</span> <span class="n">e</span><span class="p">:</span><span class="mi">20</span></span></code></pre>
          <h2 id="the-offset-clause">The offset clause</h2>
          <p>
            The offset clause skips the given number of the beginning entries.
            It has the below form:
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">offset</span> <span class="o">&lt;</span><span class="k">scope</span><span class="o">&gt;</span><span class="p">:</span><span class="o">&lt;</span><span class="n">number</span><span class="o">&gt;</span><span class="p">[,</span> <span class="o">&lt;</span><span class="k">scope</span><span class="o">&gt;</span><span class="p">:</span><span class="o">&lt;</span><span class="n">number</span><span class="o">&gt;</span><span class="p">[,</span> <span class="o">&lt;</span><span class="k">scope</span><span class="o">&gt;</span><span class="p">:</span><span class="o">&lt;</span><span class="n">number</span><span class="o">&gt;</span><span class="p">]]</span></span></code></pre>
          <p>
            Where the <code>&lt;scope&gt;</code> placeholder refers to the scope
            on which to impose the limit given by the corresponding
            <code>&lt;number&gt;</code> placeholder.
          </p>
          <p>
            E.g., the below query returns all but the first five logs, all but
            the first ten traces per log, and all but twenty events per trace.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">offset</span> <span class="n">l</span><span class="p">:</span><span class="mi">5</span><span class="p">,</span> <span class="n">t</span><span class="p">:</span><span class="mi">10</span><span class="p">,</span> <span class="n">e</span><span class="p">:</span><span class="mi">20</span></span></code></pre>
          <p>
            The below query combines <code>limit</code> and
            <code>offset</code> clauses to skip the first ten logs and return at
            most five logs, for each log skip the first twenty traces and return
            at most ten traces, and for each trace skip the first forty events
            and return at most twenty events.
          </p>
          <pre
            class="code highlight"
            lang="sql"
          ><code><span id="LC1" class="line" lang="sql"><span class="k">limit</span> <span class="n">l</span><span class="p">:</span><span class="mi">5</span><span class="p">,</span> <span class="n">t</span><span class="p">:</span><span class="mi">10</span><span class="p">,</span> <span class="n">e</span><span class="p">:</span><span class="mi">20</span></span>
<span id="LC2" class="line" lang="sql"><span class="k">offset</span> <span class="n">l</span><span class="p">:</span><span class="mi">10</span><span class="p">,</span> <span class="n">t</span><span class="p">:</span><span class="mi">20</span><span class="p">,</span> <span class="n">e</span><span class="p">:</span><span class="mi">40</span></span></code></pre>
          <h1 class="headline">Comments</h1>
          <p>
            The comment is a sequence of characters that is not interpreted as
            PQL code. The comments are intended for code documentation. PQL
            supports the C-style inline comments
            <code>// &lt;comment&gt;</code> and the SQL-style inline comments
            <code>-- &lt;comment&gt;</code> that begin from a comment prefix and
            ends at the closest newline character or end of input. PQL also
            supports the C-style block comments
            <code>/* &lt;comment&gt; */</code>. The block comments may span
            several lines or a part of a single line.
          </p>
          <h1 class="headline">Known limitations</h1>
          <p>
            PQL currently does not support some features of the
            <a
              href="https://standards.ieee.org/standard/1849-2016.html"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >IEEE 1849-2016 Standard for eXtensible Event Stream (XES) for
              Achieving Interoperability in Event Logs and Event Streams</a
            >. These features are subject to implementation in a future version
            of PQL. This section summarizes the unsupported features.
          </p>
          <h2>Nested attributes</h2>
          <p>
            The
            <a
              href="https://standards.ieee.org/standard/1849-2016.html"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >IEEE 1849-2016 standard</a
            >
            defines <em>nested attributes</em> as an optional feature for
            implementing software, not required for compliance with the IEEE
            1849-2016 standard. In the IEEE 1849-2016 standard the attributes
            may form a tree, where every node represents a single attribute and
            the root attribute is attached to a log, a trace, or an event.
          </p>
          <p>
            PQL does not support references to the nested attributes. However,
            the
            <a href="#reference-implementation"
              >reference implementation of PQL</a
            >
            supports selecting the entire tree of attributes wherever the tree
            root attribute is selected. The reference implementation of PQL does
            not use or interpret the values of the nested attributes.
          </p>
          <h2>List attribute</h2>
          <p>
            The
            <a
              href="https://standards.ieee.org/standard/1849-2016.html"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >IEEE 1849-2016 standard</a
            >
            defines the <em>list attribute</em> as an attribute which value is a
            list of other attributes.
          </p>
          <p>
            PQL does not support references to the elements of the list
            attribute, however the list attribute itself can be referenced by
            name. The
            <a href="#reference-implementation"
              >reference implementation of PQL</a
            >
            supports retrieval of the list attribute and its elements wherever
            the list attribute is referenced in the
            <a href="#the-select-clause"><code>select</code></a> clause. In all
            other clauses, the value of the list attribute evaluates to
            <code>null</code>.
          </p>
          <h1 id="reference-implementation" class="headline">
            Reference implementation
          </h1>
          <p>
            <a
              href="https://processm.cs.put.poznan.pl"
              rel="nofollow noreferrer noopener"
              target="_blank"
              >ProcessM</a
            >
            software includes the reference implementation of PQL.
          </p>
        </div>
      </v-col>
    </v-row>
  </v-container>
</template>
