package com.henry.chord

import org.json.JSONArray
import org.json.JSONObject

/** Import boundary only: existing library/editor drafts retain backward-compatible decoding. */
object ImportSyntax {
    private val songFields=setOf("schemaVersion","songId","id","title","artist","album","year","language","genre","tags","artwork","musicLinks","links","arrangements")
    private val arrangementFields=setOf("arrangementId","arrangementUuid","name","originalKey","key","tonalMode","tempoBpm","timeSignature","sourceId","sourceFormat","provenance","licenseNotes","comments","sections")
    private fun fail(path: String, message: String): Nothing = throw IllegalArgumentException("$path: $message")
    private fun fields(obj: JSONObject, allowed: Set<String>, path: String) { obj.keys().forEach { if (it !in allowed) fail("$path.$it","unknown field; check its spelling.") } }
    private fun string(obj: JSONObject, key: String, path: String, required: Boolean=false): String? {
        if (!obj.has(key) || obj.isNull(key)) { if (required) fail("$path.$key","a text value is required."); return null }
        val value=obj.get(key); if (value !is String) fail("$path.$key","expected text in double quotes.")
        var index=0
        while (index<value.length) {
            val c=value[index++]
            if (c.isHighSurrogate()) { if (index>=value.length || !value[index++].isLowSurrogate()) fail("$path.$key","invalid Unicode surrogate pair.") }
            else if (c.isLowSurrogate()) fail("$path.$key","invalid Unicode surrogate pair.")
        }
        return value
    }
    private fun number(obj: JSONObject,key: String,path: String,integer: Boolean=false): Number? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val n=obj.get(key); if (n !is Number || !n.toDouble().isFinite() || integer && n.toDouble()%1.0!=0.0) fail("$path.$key",if (integer) "expected a whole number, without quotes." else "expected a finite number, without quotes.")
        return n
    }
    private fun array(obj: JSONObject,key: String,path: String,required: Boolean=false): JSONArray? {
        if (!obj.has(key) || obj.isNull(key)) { if (required) fail("$path.$key","an array is required."); return null }
        return obj.get(key) as? JSONArray ?: fail("$path.$key","expected an array [...].")
    }
    private fun obj(value: Any,path: String) = value as? JSONObject ?: fail(path,"expected an object {...}.")
    private fun JSONArray.each(path: String,action: (JSONObject,String)->Unit) { for (i in 0 until length()) action(obj(get(i),"$path[$i]"),"$path[$i]") }
    private fun optionalObject(parent: JSONObject,key: String,path: String,action: (JSONObject,String)->Unit) { if (parent.has(key) && !parent.isNull(key)) action(obj(parent.get(key),"$path.$key"),"$path.$key") }
    private fun id(obj: JSONObject,key: String,path: String,required: Boolean=false) { string(obj,key,path,required)?.let { if (it.isBlank() || it.length>256) fail("$path.$key","ID must contain 1–256 characters.") } }

    fun native(text: String): List<ParsedDocument> {
        JsonSyntax(text).validate()
        val root=JSONObject(text)
        if (root.optString("format")=="chordroom-collection") {
            fields(root,setOf("format","schemaVersion","songs"),"$")
            if (number(root,"schemaVersion","$",true)?.toDouble()!=1.0) fail("$.schemaVersion","use 1.")
            val songs=array(root,"songs","$",true)!!
            if (songs.length() !in 1..2000) fail("$.songs","expected 1–2000 songs.")
            songs.each("$.songs") { entry,path -> song(entry,path,true) }
        } else song(root,"$",false)
        // Semantic checks for key/mode, meter, ID uniqueness and canonical limits are still required.
        val parsed=Canonical.decode(text)
        parsed.forEachIndexed { di,entry -> entry.document.arrangements.forEachIndexed { ai,arrangement -> arrangement.sections.forEachIndexed { si,section -> section.lines.forEachIndexed { li,line ->
            val boundaries=SheetLayout.boundaries(line.lyric).toSet()
            var offset=0
            line.segments.forEachIndexed { ti,segment ->
                if (offset !in boundaries) fail("${if (parsed.size>1) "$.songs[$di]" else "$"}.arrangements[$ai].sections[$si].lines[$li].segments[$ti]","segment splits a Unicode character/emoji sequence; keep the complete character in one segment.")
                offset+=segment.text.length
            }
        } } } }
        return parsed
    }

    private fun song(song: JSONObject,path: String,inheritedVersion: Boolean) {
        fields(song,songFields+arrangementFields,path)
        if ((!inheritedVersion || song.has("schemaVersion")) && number(song,"schemaVersion",path,true)?.toDouble()!=1.0) fail("$path.schemaVersion","use 1; old library data is migrated separately.")
        if (song.has("songId") && song.has("id")) fail(path,"use songId rather than both songId and id.")
        id(song,if (song.has("songId")) "songId" else "id",path,true)
        listOf("title","artist").forEach { key -> if (string(song,key,path,true)!!.isBlank()) fail("$path.$key","must not be blank.") }
        listOf("album","language","genre").forEach { string(song,it,path) }
        number(song,"year",path,true)?.let { if (it.toInt() !in 1000..9999) fail("$path.year","use a four-digit year.") }
        array(song,"tags",path)?.let { tags -> for (i in 0 until tags.length()) if (tags.get(i) !is String || tags.getString(i).isBlank()) fail("$path.tags[$i]","expected nonblank text.") }
        optionalObject(song,"artwork",path) { art,p ->
            fields(art,setOf("path","sourceUrl","credit"),p); listOf("path","sourceUrl","credit").forEach { string(art,it,p) }
            art.string("path")?.let { relativePath(it,"$p.path") }
            art.string("sourceUrl")?.let { if (!httpUrl(it)) fail("$p.sourceUrl","expected an http(s) source URL.") }
        }
        if (song.has("musicLinks") && song.has("links")) fail(path,"use either musicLinks or links, not both.")
        for (key in listOf("musicLinks","links")) optionalObject(song,key,path) { links,p ->
            fields(links,setOf("spotifyTrackUrl","youtubeVideoUrl","spotify","youtube"),p)
            listOf("spotifyTrackUrl" to LinkService.SPOTIFY,"spotify" to LinkService.SPOTIFY,"youtubeVideoUrl" to LinkService.YOUTUBE,"youtube" to LinkService.YOUTUBE).forEach { (field,service) ->
                string(links,field,p)?.let { if (SongLinks.valid(it,service)==null) fail("$p.$field","expected a valid ${service.name.lowercase()} track/video URL, or null.") }
            }
        }
        val arrangements=array(song,"arrangements",path)
        if (arrangements!=null) {
            if (song.keys().asSequence().any { it in arrangementFields }) fail(path,"put key, sections and other arrangement fields inside arrangements; do not mix the two layouts.")
            if (arrangements.length() !in 1..100) fail("$path.arrangements","expected 1–100 arrangements.")
            arrangements.each("$path.arrangements") { a,p -> fields(a,arrangementFields,p); arrangement(a,p) }
        } else arrangement(song,path)
    }

    private fun arrangement(a: JSONObject,path: String) {
        val seenIds=mutableSetOf<String>()
        fun unique(obj: JSONObject,p: String) { id(obj,"id",p); obj.string("id")?.let { if (!seenIds.add(it)) fail("$p.id","duplicate section/line/segment/anchor ID '$it'.") } }
        listOf("arrangementId","arrangementUuid").forEach { id(a,it,path) }
        listOf("name","originalKey","key","tonalMode","timeSignature","sourceId","sourceFormat","provenance","licenseNotes","comments").forEach { string(a,it,path) }
        val key=a.string("originalKey") ?: a.string("key")
        if (key!=null && !Canonical.keyValid(key)) fail("$path.originalKey","invalid key '$key'; examples: C, Bb, F#m.")
        val mode=a.string("tonalMode")
        if (mode!=null && mode !in listOf("major","minor")) fail("$path.tonalMode","use major, minor or null.")
        if (key?.endsWith("m")==true && mode=="major") fail("$path.tonalMode","a key ending in m requires minor mode.")
        number(a,"tempoBpm",path)?.let { if (it.toDouble() !in 10.0..500.0) fail("$path.tempoBpm","use 10–500 BPM.") }
        a.string("timeSignature")?.let { if (!validMeter(it)) fail("$path.timeSignature","expected a meter such as 4/4 or 6/8.") }
        val sections=array(a,"sections",path,true)!!
        if (sections.length()==0) fail("$path.sections","add at least one section.")
        var content=false
        sections.each("$path.sections") { section,p ->
            fields(section,setOf("id","type","label","lines"),p); unique(section,p); string(section,"label",p)
            string(section,"type",p,true)?.let { if (it !in Canonical.sectionTypes) fail("$p.type","unknown section type '$it'; use custom for your own heading.") }
            val lines=array(section,"lines",p,true)!!
            if (lines.length()==0) fail("$p.lines","add a lyric, instrumental or blank line.")
            lines.each("$p.lines") { line,lp ->
                fields(line,setOf("id","segments","annotation"),lp); unique(line,lp)
                string(line,"annotation",lp)?.let { if (it.isNotBlank()) content=true }
                array(line,"segments",lp,true)!!.each("$lp.segments") { segment,sp ->
                    fields(segment,setOf("id","text","chord","anchor"),sp); unique(segment,sp)
                    val text=string(segment,"text",sp,true)!!
                    if (text.any { it=='\r' || it=='\n' || it=='\u0000' }) fail("$sp.text","put each line break in a separate line object; NUL is not allowed.")
                    if (text.isNotBlank()) content=true
                    if (segment.has("chord") && segment.has("anchor")) fail(sp,"use chord or anchor, not both.")
                    string(segment,"chord",sp)?.let { chord(it,"$sp.chord"); content=true }
                    optionalObject(segment,"anchor",sp) { anchor,ap ->
                        fields(anchor,setOf("id","rawChord","beat","measure","annotations"),ap); unique(anchor,ap); string(anchor,"annotations",ap)
                        chord(string(anchor,"rawChord",ap,true)!!,"$ap.rawChord"); content=true
                        number(anchor,"beat",ap)?.let { if (it.toDouble()<0) fail("$ap.beat","must be nonnegative.") }
                        number(anchor,"measure",ap,true)?.let { if (it.toDouble()<0) fail("$ap.measure","must be nonnegative.") }
                    }
                }
            }
        }
        if (!content) fail("$path.sections","arrangement contains no lyrics, annotations or chords.")
    }
    private fun chord(raw: String,path: String) { if (raw.isBlank() || raw.length>128 || ChordParser.parse(raw)==null) fail(path,"unsupported chord '$raw'; examples: C, Am7, D/F#, F#m7b5, N.C. Put prose in annotation/text.") }
    private fun validMeter(value: String)=value.matches(Regex("(?:[1-9]|[1-2][0-9]|3[0-2])/(?:1|2|4|8|16|32)"))
    private fun httpUrl(value: String)=runCatching { java.net.URI(value).let { it.scheme in listOf("http","https") && !it.host.isNullOrBlank() && it.userInfo==null } }.getOrDefault(false)
    private fun relativePath(value: String,path: String) { if (value.isBlank() || value.startsWith('/') || value.contains('\\') || value.contains(':') || value.split('/').any { it.isEmpty() || it=="." || it==".." || it.any { c -> c.code<32 } }) fail(path,"use a safe relative path, e.g. assets/cover.png; no ../ or absolute paths.") }

    fun chordPro(text: String) {
        var active: String?=null
        text.replace("\r\n","\n").replace('\r','\n').lines().forEachIndexed { index,line ->
            val p="Line ${index+1}"; val trimmed=line.trim()
            if (trimmed.startsWith('{') || trimmed.endsWith('}')) {
                val match=Regex("^\\{([^{}:]+)(?::([^{}]*))?\\}$").matchEntire(trimmed) ?: fail(p,"malformed directive; use {title: Name} or {start_of_chorus} on its own line.")
                val name=match.groupValues[1].trim().lowercase(); val value=match.groupValues[2].trim()
                val aliases=mapOf("soc" to "chorus","sov" to "verse","sob" to "bridge","soi" to "intro","eoc" to "chorus","eov" to "verse","eob" to "bridge","eoi" to "intro")
                val type=(aliases[name] ?: name.removePrefix("start_of_").removePrefix("end_of_")).replace("pre_chorus","prechorus").replace("post_chorus","postchorus")
                when {
                    name.startsWith("start_of_") || name in listOf("soc","sov","sob","soi") -> { if (type !in Canonical.sectionTypes) fail(p,"unknown section '$type'."); active=type }
                    name.startsWith("end_of_") || name in listOf("eoc","eov","eob","eoi") -> { if (active!=type) fail(p,"section end '$type' does not match open section '${active ?: "none"}'."); active=null }
                    name=="key" -> if (!Canonical.keyValid(value)) fail(p,"invalid key '$value'.")
                    name=="tempo" -> if (value.toDoubleOrNull()?.let { it.isFinite() && it in 10.0..500.0 }!=true) fail(p,"tempo must be 10–500 BPM.")
                    name=="time" -> if (!validMeter(value)) fail(p,"invalid time signature '$value'.")
                    name=="year" -> if (value.toIntOrNull() !in 1000..9999) fail(p,"year must be four digits.")
                    name in setOf("title","t","artist","subtitle","st","album","language","genre","source","source_id","license","copyright","comment","c") -> if (value.isBlank()) fail(p,"{$name: ...} needs a value.")
                    else -> fail(p,"unsupported directive {$name}; use {comment: ...} for a note.")
                }
            } else {
                var i=0
                while (i<line.length) {
                    when (line[i]) {
                        '[' -> { val end=line.indexOf(']',i+1); if (end<0) fail("$p, column ${i+1}","missing closing ] for chord."); chord(line.substring(i+1,end),"$p, column ${i+1}"); i=end }
                        ']' -> fail("$p, column ${i+1}","closing ] has no opening [.")
                        '{','}' -> fail("$p, column ${i+1}","directives must occupy their own line; use JSON text for literal braces.")
                        '\u0000' -> fail(p,"NUL/binary data is not allowed.")
                    }; i++
                }
            }
        }
    }
}

/** org.json accepts non-JSON syntax. This scanner rejects it before decoding/coercion. */
internal class JsonSyntax(private val text: String) {
    private var i=0
    private fun fail(message: String): Nothing { val prefix=text.take(i); throw IllegalArgumentException("JSON line ${prefix.count { it=='\n' }+1}, column ${i-(prefix.lastIndexOf('\n')+1)+1}: $message") }
    private fun space() { while (i<text.length && text[i] in " \t\n\r") i++ }
    private fun expect(c: Char) { space(); if (text.getOrNull(i)!=c) fail("expected '$c'."); i++ }
    fun validate() { space(); if (text.getOrNull(i)!='{') fail("song must be a JSON object."); value(0); space(); if (i!=text.length) fail("unexpected text after JSON object.") }
    private fun quoted(): String {
        expect('"'); val start=i
        while (i<text.length) {
            val c=text[i++]
            if (c=='"') return text.substring(start,i-1)
            if (c.code<32) fail("escape control characters inside text.")
            if (c=='\\') { val escape=text.getOrNull(i++) ?: fail("unfinished escape."); if (escape=='u') { repeat(4) { val digit=text.getOrNull(i++) ?: fail("unfinished Unicode escape."); if (digit !in "0123456789abcdefABCDEF") fail("invalid Unicode escape.") } } else if (escape !in "\"\\/bfnrt") fail("invalid escape \\$escape.") }
        }
        fail("missing closing double quote.")
    }
    private fun value(depth: Int) {
        if (depth>64) fail("nesting exceeds 64 levels.")
        space()
        when (text.getOrNull(i)) {
            '{' -> { i++; space(); val keys=mutableSetOf<String>(); if (text.getOrNull(i)=='}') { i++; return }; while (true) {
                val key=quoted(); val decoded=JSONObject("{\"$key\":null}").keys().next(); if (!keys.add(decoded)) fail("duplicate field '$decoded'.")
                expect(':'); value(depth+1); space(); if (text.getOrNull(i)=='}') { i++; break }; expect(',')
            } }
            '[' -> { i++; space(); if (text.getOrNull(i)==']') { i++; return }; while (true) { value(depth+1); space(); if (text.getOrNull(i)==']') { i++; break }; expect(',') } }
            '"' -> quoted()
            't','f','n' -> { val word=when (text[i]) { 't' -> "true"; 'f' -> "false"; else -> "null" }; if (!text.startsWith(word,i)) fail("invalid literal."); i+=word.length }
            '-', in '0'..'9' -> { val m=Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").find(text,i); if (m==null || m.range.first!=i) fail("invalid number."); i=m.range.last+1 }
            else -> fail("expected an object, array, double-quoted text, number, true, false or null.")
        }
    }
}
