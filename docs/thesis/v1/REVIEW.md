# Review of the Masterarbeit (status 07.11.2026)

**Files**

- `Masterarbeit.docx`: your original, unchanged.
- `Masterarbeit_überarbeitet.docx`: the revised version. Every change is a **tracked change** (author "Claude (Review)"). In Word, open *Überprüfen → Änderungen* to accept or reject each change one by one.

I checked the claims in the thesis against the real code in `/root/design-daas-backend-java` (version 2.0.0), `/root/design-daas-application-frontend` and `/root/design-daas`. Several problems were only visible this way.

---

## 1. Critical problems (fixed)

| # | Problem | What I changed |
|---|---|---|
| 1 | **Chapter 5 came from an old Bachelor thesis** (SDN controller, Asterisk, ONOS, OpenStack). | Replaced it with a new chapter: 5.1 Zusammenfassung, 5.2 Beantwortung der Forschungsfrage, 5.3 Abgleich mit den Anforderungen (Tabellen 5.1/5.2), 5.4 Kritische Reflexion und Grenzen, 5.5 Ausblick, 5.6 Schlussbetrachtung. |
| 2 | **Codeverzeichnis came from the same old thesis** (27 entries about Docker, Kubernetes and ONOS). | Replaced it with the 28 real code listings of chapter 4. |
| 3 | **Several endpoints in the thesis don't exist in the code.** `GET /version`, `GET /users/access/{desktopId}` (FA-12), `GET/DELETE /user/{id}`, `GET/DELETE /admin/{id}`, `/ping/*` and the `endpoint_group` endpoints are all missing. Enable/disable is `PATCH`, not `POST`. The thesis still claimed "jeder Endpunkt … muss angeboten werden" and marked every FA as fulfilled. | Added a new **Tabelle 3.5** in 3.2.2 with a client-usage analysis: which old endpoints the frontend and DaaS app actually call. This turns the reduced API into a deliberate, justified decision. Chapter 5 now honestly rates FA-6, FA-7, FA-11 and NFA-3 as *teilweise* and FA-12 as *nicht umgesetzt*. |
| 4 | **The research question contradicted the Abgrenzung.** It said "ohne Anpassung", but you changed the frontend (PATCH, logout) and the DaaS app (`role`, removed `/permissions_info` call). | Reworded it to "ohne Änderungen an ihrer fachlichen Logik". Section 1.5 now lists the client changes concretely. **Please confirm this wording with Prof. Baun.** |
| 5 | **Demonstrator paths were wrong.** For example `GET /permission?user_id=…`, `/desktop-group/{id}/user-group/{id}` and `POST /user/{id}/request-application`. | Corrected them to the real paths. |
| 6 | **Wrong code excerpts.** Code 4.2 used layer names that don't match the real ArchUnit rule ("Controller" instead of "Controllers", and `de.frauas.design...`). Code 4.4 used the old hard-coded `DUMMY_HASH`. Code 4.23 still contained `/version`. | Brought all three in line with version 2.0.0. I also made the Code 4.2 text box taller. |
| 7 | **Factual errors in the text.** Examples: "Services nicht von Repositories abhängen" (it's the other way round). "Mergen wird verhindert" (there is no CI pipeline in the repo). "das alte Zugriffstoken verliert seine Gültigkeit" (it doesn't). A claimed ArchUnit cycle rule between user and desktop (doesn't exist). "Refresh-token reuse is detected" (it is only rejected). | Corrected each one. |

## 2. Structure, references and language (fixed)

- **Broken cross-references.** These now point to the right place: 3.4 intro (wrong subsection list), "3.4.1" → 3.4.2, "Kapitel 5 fasst AP6 zusammen" (AP6 is 4.8), "siehe 2.2.3" → 2.2.5, NFA-8/NFA-9 cited for unrelated topics, "FA-14" (doesn't exist).
- **Numbering.**
  - Two figures were both labelled "Abbildung 3.3". The second is now 3.4.
  - Five empty figure placeholders in chapter 4 are removed and point to existing figures instead.
  - The Swagger figures are now 4.1 and 4.2.
  - The tables after the new 3.5 are renumbered (3.6–3.8).
  - "Code 4.2: e", "Code 4.3: i", "Code 4.11: f" and similar placeholder captions now have real titles.
- **Abbildungs- und Tabellenverzeichnis** are completed.
- **Citations (APA).**
  - Same-author, same-year sources are now told apart: Jones 2015a/b, Jones et al. 2015a/b, VMware 2026a–d.
  - The literature list is sorted alphabetically.
  - New entries: the Ausschreibung (Baun, 2026), the Spring Framework docs, and the full author list for NIST SP 800-162.
  - Figures 2.4–2.13 and 3.1–3.4 now carry a source note ("eigene Darstellung nach …").
- **Removed text that read like private notes.** One example is the 2.2.5 paragraph "Für weiterführende, bereits fertige Diagramme eignen sich…". It is replaced by a normal literature reference.
- **Removed Markdown artifacts.** Literal backticks (`` ` ``) were scattered through chapter 3 and are gone.
- **Grammar and typos.** "etwas iss", "FA-3 habt", "Zugriffskontroll- modelle", „RFC 6749“ used as quotes, "ein in der Ausschreibung implizit mitgemeinten Symptom", "Diagnose ( admin)" and others.
- **Abkürzungsverzeichnis.** "Y" before XACML → "X". Added AF, CI, CORS, CSRF, FA, NFA, PII, PKCE, SMTP, STI and UUID.
- **New Anhang A.** A full endpoint table of the new backend (Tabelle A.1).
- **New honest details in chapter 4.**
  - The token endpoint accepts only form data and answers 415 for JSON.
  - The session response contains `role`.
  - Token lifetimes are 1 h / 30 d.
  - The token-bucket refill limitation, and that rate limits are counted per instance.
  - The default admin password must be changed before production.
  - The entropy of the registration code.
  - Why the password grant was kept despite RFC 9700.

## 3. What only you can do (marked yellow in the document)

1. **Take two Swagger screenshots** for Abbildung 4.1 (password grant) and 4.2 (permission allow/deny).
2. **Run `mvn verify`** (no Java is installed on this machine, so I couldn't).
   - Confirm the test count. Counting the code gives 31 classes = 232 `@Test` + 19 `@ArchTest` = 251.
   - Update Tabelle 4.3 (JaCoCo). The report in the thesis is from before version 2.0.0.
3. **If you tested the backend together with the frontend and DaaS app**, describe it in 4.7.2 (yellow note). It is your strongest proof of compatibility.
4. **In Word: update all indices.** Use Ctrl+A, then F9. The Code/Abbildungs/Tabellenverzeichnis entries are typed by hand and have no page numbers. Better: use *Verweise → Beschriftung einfügen* for captions and *Abbildungsverzeichnis einfügen* for the lists, so they update automatically.
5. **Check Tabelle 3.2 against the old PHP code.** It shows `/desktop-groups` with a hyphen, but the frontend uses `/desktop_groups`. Either the table has a typo or the frontend was changed. I couldn't verify this because the old PHP repository isn't on this machine.
6. **Join a broken sentence in 2.1.1.** "…I/O-Bandbreite ¶ ein Container maximal verbrauchen darf" is split across two paragraphs.
7. **Running header.** In the PDF render, chapter 3 pages show "4 Realisierung". Check the section breaks and headers in Word.
8. **Abbildung 2.2** comes from a Substack blog, which is a weak source for a Master thesis. Better: draw it yourself, or cite the Docker documentation.
9. **Popek & Goldberg (1974)** is commonly cited for virtualization requirements. The *Typ-1/Typ-2* classification is usually attributed to Goldberg (1973). Either add that source or reword.
10. **Title page.** Add Studiengang / Fachbereich / "zur Erlangung des Grades Master of Science" if your Prüfungsordnung requires it.
11. Confirm that all figures marked "eigene Darstellung" were really made by you.

## 4. Plagiarism and AI use

- I have no access to a plagiarism checker. The text I wrote (chapter 5, the new paragraphs and the rewrites) is newly formulated and not copied from any source. Chapter 2 paraphrases documentation and RFCs; the paraphrases are cited and look acceptable, but some sentences stay close to the Spring and RFC wording.
- **Run the university's plagiarism check** (FRA-UAS offers one through the library or Prüfungsamt) before you submit.
- **Important:** your Eidesstattliche Erklärung says you wrote the work yourself. Many universities, including FRA-UAS faculties, now require you to **declare the use of AI tools**. Please check your Prüfungsordnung or ask Prof. Baun, and add a declaration if required. Using AI help without declaring it can be treated like plagiarism.

## 5. Alignment with the Ausschreibung (08.11.2026, Markdown files only)

| Ausschreibung says | Gap in the thesis | Change |
|---|---|---|
| New backend "in Python oder einer anderen modernen Programmiersprache" | Java was never justified | 1.3: four reasons for Java 21 + Spring Boot, Python named as alternative; 5.4: trade-off of two ecosystems (Python DaaS app + Java backend) |
| Old backend: quality weak **and documentation bad** | AP6 "Dokumentation" not covered at all | New NFA-10 (documentation), new **section 4.9 Dokumentation (AP6)** with Tabelle 4.5 of the repo docs, Javadoc coverage, package-info files and the docs-update rule |
| Other parts "lassen sich relativ einfach installieren" | not addressed | New NFA-11 (installation), installation paragraph in 4.7.1 |
| DaaS app and frontend "ausreichend gut dokumentiert" | misquoted as "gut dokumentiert" | Corrected in 1.1 and 3.1 |
| No statement about keeping frontend/DaaS app unchanged | thesis said the Ausschreibung requires it | 1.2: now clearly your own decision |
| Frontend "in Python entwickelt" | thesis silently said React | Chapter 1 intro names the discrepancy and says the repos are the basis |
| "Das Projekt ist abgeschlossen", repo URLs | not mentioned | 1.1 motivation (no original team left), repo names in 3.1.1 |
| AP1–AP6 | AP mapping only in prose; AP3 called "Entwurf" | Tabelle 1.1 (AP → section), Tabelle 5.3 (AP → result) |
| Source: Informatik-Spektrum 47(5–6), 2024 | not cited | Cited in 2.1 as Atanassov, Baun & Bouché (2024); added to references. MobiWis reference completed (LNCS 14792, pp. 3–18, DOI). Metadata checked on Crossref. |

Chapter 5 counts updated (11 NFAs, "Sieben der elf"). The italic formatting on the Ausblick bullets was removed (a Word formatting carry-over). "LTS" was added to the abbreviations.

**Please check:**
- The four reasons for choosing Java in 1.3 must be your real reasons. Adjust them if not.
- Section 4.9 quotes the Javadoc numbers (122 types, 96 public methods) from `docs/development.md` in the backend repo.

**The `.docx` does not contain these changes yet.** Until they are carried over, the Markdown files are the newer version.

## 6. Second review of the Markdown chapters (08.11.2026)

### Checked against the sources
- **Old PHP backend** (cloned from `nuromedia/design-daas-application-backend`):
  - Tabelle 3.2 had three errors, now fixed: `GET /desktop_groups` (underscore), `POST /admin` (not `/admin/{id}`), and the missing `POST /user`. I also added the old Swagger routes.
  - Tabelle 3.5 is corrected to match. `POST /user` is not new; only `resend_code` and `logout` are.
  - Confirmed as written: 69 test files, 3 PHPat rules, single-table inheritance, the `permissions:import` CLI command.
- **Java backend:**
  - The application-request email goes to the system's own mailbox with the user as reply-to, not "an die zuständigen Administratoren". Fixed in AF-8 and 4.4.4.
  - Confirmed: token cleanup runs daily at 03:00, the logging levels, the session-invalidation listener, and the debug log of the registration code.

### Content added
- **Section 2.5 Migration von Altsystemen und Wartbarkeit.** The research question is about migration and *Wartbarkeit*, but chapter 2 gave neither concept a foundation. Now:
  - Reverse engineering (Chikofsky & Cross, 1990)
  - Strangler-fig vs. complete replacement (Fowler, 2004)
  - Characterization tests (Feathers, 2004)
  - Maintainability per ISO/IEC 25010 with its five sub-characteristics
- **Chapter 5.2:** the measures are now mapped onto the ISO/IEC 25010 sub-characteristics. **5.4:** new paragraph on the replacement strategy.
- **Theory that was never used later is now used:**
  - RBAC/ACL/ABAC and PEP/PDP classify the backend's permission model (4.3 intro).
  - The Client-Credentials Grant and OpenID Connect appear as Ausblick items (the DaaS app currently logs in with a password grant).
  - TLS appears in the operations view (4.7.1).
  - Mockito appears in the test section (4.8.2).
- **Figures that were never referenced** (2.2, 2.3, 2.6, 3.1, 3.3) now are.
- **Consistency:** "Kapitel 3.2" etc. → "Abschnitt 3.2". New references and abbreviations ISO and IEC.

### New figures (`images/`, PNG plus editable SVG; generator: `images/make_diagrams.py`)
| Figure | Content | Why |
|---|---|---|
| Abb. 3.3 (redrawn) | Target architecture | The old version said the clients are "unverändert", which contradicts 1.5. The old file is kept as `abb-3-3-alt.png`. |
| Abb. 4.1 | Checks in the password grant with all error paths and status codes | 4.2.2 described six checks in prose only |
| Abb. 4.2 | Token lifecycle: issue, check, rotate, revoke, cleanup | Spread across 4.2.3–4.2.5 and 4.6.6 |
| Abb. 4.3 | Deployment view: TLS proxy, container, key volume, MariaDB, SMTP | Supports NFA-7/NFA-11 and the TLS requirement |

Chapter 4 previously had **no figure at all** next to 28 code listings. The Swagger screenshots are now Abb. 4.4 and 4.5 and **still need to be taken by you.**

### Suggestions I did not apply (your decision)
- **Fewer code listings in chapter 4.** Code 4.7 (refresh-token bytes), 4.25 (OpenAPI config) and 4.27 (`.env`) add little. They could move to the Anhang or be dropped.
- **Chapter 2 is longer than chapter 3** (≈5,900 vs. ≈5,000 words). If you run over the page limit, shorten 2.2.1 (grant types) and 2.3.1 (ACL/RBAC/ABAC) first.
- **The old backend repository is reachable.** Characterization tests that compare the old and new API would turn the biggest limitation (5.4) into a strength. That is probably too much for the remaining time.

The `.docx` still has none of the changes from sections 5 and 6.

## 7. Language revision of chapters 1–5 (08.11.2026)

Goal: correct German that reads like it was written by a person, in an academic register, with the same content.

| | Before | After |
|---|---|---|
| Average sentence length | 21–30 words (chapter 2: 30.5) | 17–20 words |
| Sentences > 35 words | 142 | 26 |
| Sentences > 50 words | 45 | 8 (mostly bullet lists counted as one sentence) |
| Dashes (–/—) | 67 | 0 |
| "bewusst" / "explizit" / "bemerkenswert" | 24 / 15 / 4 | 1 / 0 / 0 |
| "nicht nur … sondern" | 7 | 1 |

**What changed:**
- Long sentences with stacked nominal phrases and brackets were split.
- Passive constructions were made active where it reads better.
- Meta phrases were removed ("Dies ist die direkte technische Antwort …").
- Anglicisms were replaced where a natural German term exists: Build-Gate, Login/Logout, Session, User-Group, Fachmodul, Request.
- Straight quotes became „…“.
- Some headings were made clearer (4.2.1, 4.2.5, 4.2.7, 4.3, 4.6.4, 4.8, 4.8.4).

**Factual fixes made along the way:**
- 3.4.4 described the old PHPat rules wrongly (they don't cover a "Util" module). Corrected to the three rules found in the old code.
- Several places still said the clients are "nicht verändert". They now say the clients were "höchstens in ihrer Anbindung angepasst".
- 5.4 claimed the Ausschreibung "prefers" Python. It only names Python first.
- 4.8.5 "nachweisbar durch einen aktuell grünen Build" was softened, because the build could not be run here.
- The sentence split across two paragraphs in 2.1.1 is merged.
- Broken italic markers in 4.7.2 and 4.8.2 are fixed.

**Unchanged:** all code listings, tables (except one cell), figure, table and code captions, and all citations.

Backup of the chapters before this revision: `/tmp/claude-1000/-root-design-daas-master-report/7ea4fbfc-b5a6-445e-a6bd-a414bae17037/scratchpad/md-before-language/`

**Please read it once yourself.** You know best how you write. If a sentence doesn't sound like you, change it; your examiner may ask about passages you can't explain in your own words.

## 8. Markdown version

The `chapter-*.md` files and `00-`, `06-`, `07-` and `08-*.md` were generated from `Masterarbeit_überarbeitet.docx` with all tracked changes accepted. Figures are stored as `images/abb-*.png`. The old Markdown drafts are kept in `archiv-alte-md/`. The `.docx` stays the reference version: if you accept or reject changes in Word, regenerate the Markdown files.
