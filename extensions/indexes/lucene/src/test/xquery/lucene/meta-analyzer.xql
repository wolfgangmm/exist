xquery version "3.1";

module namespace ftt="http://exist-db.org/xquery/meta-analyzer/test";

declare namespace test="http://exist-db.org/xquery/xqsuite";
declare namespace stats="http://exist-db.org/xquery/profiling";

declare variable $ftt:COLLECTION_CONFIG :=
    <collection xmlns="http://exist-db.org/collection-config/1.0">
        <index xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <lucene>
                <analyzer class="org.exist.indexing.lucene.analyzers.MultiLanguageAnalyzer"/>
                <text qname="div" />
                <text qname="p" />
            </lucene>
        </index>
    </collection>;

declare variable $ftt:DATA :=
    <body>
        <div>
            <p>No lang / default</p>
            <div>
                <p>text in nested div and more <hi>text</hi>.</p>
            </div>
        </div>
        <div xml:lang="en">
            <p xml:lang="es">Spanish child p in english div</p>
            <div>
                <p>english text in nested div and more <hi>text</hi>.</p>
            </div>
        </div>
        <div xml:lang="fr">
            <p>D’histoire</p>
            <div>
                <p>french text in nested div and more... <hi>Histoire</hi>.</p>
            </div>
        </div>
        <div>
            <p xml:lang="fr">L’histoire</p>
            <div xml:lang="en">
                <p>d’histoire de nuit.</p>
            </div>
        </div>
        <div xml:lang="ja">日本の保育園</div>
    </body>;

declare variable $ftt:COLLECTION_NAME := "metaanalyzertest";
declare variable $ftt:COLLECTION := "/db/" || $ftt:COLLECTION_NAME;

declare
    %test:setUp
function ftt:setup() {
    xmldb:create-collection("/db/system/config/db", $ftt:COLLECTION_NAME),
    xmldb:store("/db/system/config/db/" || $ftt:COLLECTION_NAME, "collection.xconf", $ftt:COLLECTION_CONFIG),
    xmldb:create-collection("/db", $ftt:COLLECTION_NAME),
    xmldb:store($ftt:COLLECTION, "test.xml", $ftt:DATA)
};

declare
    %test:tearDown
function ftt:cleanup() {
    xmldb:remove($ftt:COLLECTION),
    xmldb:remove("/db/system/config/db/" || $ftt:COLLECTION_NAME)
};

(:~
 : It should find all three occurences of "histoire" because the correct FrenchAnalyzer was chosen.
 :)
declare
    %test:args("histoire")
    %test:assertEquals(2,1)
function ftt:highlight($query as xs:string) {
    count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., $query)])//exist:match),
    count(util:expand(collection($ftt:COLLECTION)//p[ft:query(., $query)])//exist:match)
};

(:~
 : It should find two results because "日本" is two words
 :)
declare
    %test:args("日本")
    %test:assertEquals(2)
function ftt:query-japanese($query as xs:string) {
    count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., $query)])//exist:match)
};

(:~
 : ft:query using the english analyzer should return only 1 match for "L’histoire"
 :)
declare
    %test:args("L’histoire", "en")
    %test:assertEquals(1)
function ftt:test-en-analyzer($query as xs:string, $lang) {
    let $options := <options><lang>{$lang}</lang></options>
    return count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., $query, $options)])//exist:match)
};

(:~
 : ft:query using french analyzer should return all matches for "histoire"
 :)
declare
    %test:args("L’histoire", "fr")
    %test:assertEquals(3)
function ftt:test-fr-analyzer($query as xs:string, $lang) {
    let $options := <options><lang>{$lang}</lang></options>
    return count(util:expand(collection($ftt:COLLECTION)//div[ft:query(., $query, $options)])//exist:match)
};
