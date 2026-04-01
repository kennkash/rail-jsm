# How Portal Search Works

The portal search has been updated to make it easier to find the right result, even if your search is not perfectly spelled.

## What you can search for

You can search across:

- **Portal names**
- **Project keys**
- **Project descriptions / keywords**
- **Request type names**

This means you can find results by searching for:
- the portal name itself
- an abbreviation or project key
- keywords mentioned in the portal description
- the name of a request type

## What is changing

Search now supports **fuzzy matching**.

That means if you make a small spelling mistake, the search can still return the result you probably meant.

### Examples
- `Jera` can still match **Jira**
- `Atlassan` can still match **Atlassian**
- `poratl` can still match **portal**

## How results are matched

The search looks for the best matches in this order:

1. **Exact match**
2. **Starts with**
3. **Contains**
4. **Fuzzy match** for close misspellings

Exact matches are still prioritized over fuzzy matches, so the most relevant results should appear first.

## How results are ranked

Search results are sorted by relevance.

In general:
- strong matches in **portal names** rank highly
- matches in **project keys** also rank highly
- matches in **keywords / descriptions** can still surface useful portals
- **request types** are also included in results

If two results are similarly relevant, portal results may appear before request type results.

## Highlighting

Matched text is highlighted in the results list.

This now includes:
- exact matches
- partial matches
- fuzzy matches

So if you search for `Atlassan` and a result contains **Atlassian**, the word **Atlassian** can still be highlighted.

## Tips for better results

For the best experience:

- Use at least **2 characters**
- Try the main keyword first
- If you are unsure of the exact name, search for a related word or phrase
- Small spelling mistakes should still work

## Things to keep in mind

- Very short searches may return broader results
- Fuzzy matching helps with small misspellings, but not completely unrelated words
- Exact matches will still rank above typo-tolerant matches

## Summary

The updated search is designed to help you find portals and request types more easily by supporting:

- portal names
- project keys
- description keywords
- request type names
- typo tolerance through fuzzy matching

This should make search more forgiving and more useful when you do not know the exact wording.



---


# RAIL Portal Search Guide

## What’s changing

The landing page search is being updated so users can find both:

- **Portals**
- **Request types**

from one search box.

The goal is to make it easier to find the right place to go, even when users search by portal name, project key, keywords in a portal’s description, or a request type name.

---

## What the search looks at

The search can use information from:

### Portal results
A portal can be found from:
- **Project name**
- **Project key**
- **Keywords or terms in the project description**

Project descriptions come from the projects API, which includes a `description` field for each project.  [oai_citation:0‡projects-client.ts.txt](sediment://file_00000000a9ec71fdb8081904c5a87e37)

### Request type results
A request type can be found from:
- **Request type name**
- **Its project name**
- **Its project key**

Global request type search is enabled once the search term has at least 2 characters, and the current default request type search limit is 20.  [oai_citation:1‡use-request-types.ts.txt](sediment://file_00000000048c71fda0f5ca3ddfba8ccd)

---

## Result priority

When multiple result types match, results are ranked in this order:

1. **Project name**
2. **Project key**
3. **Project description**
4. **Request type name**

This means:
- If a portal name matches your search, that will show before a description match.
- If a portal description matches and a request type also matches, the portal can still appear first if it ranks higher.

---

## Portal-first behavior

The search is designed to help users find the **right portal first**, not only the exact request type.

For example, if:
- one portal has `Jira` in its description
- and another project has a request type named `Jira Access`

a search for `Jira` can show:
- the portal result
- the request type result

rather than forcing users to already know which one they need.

---

## Multi-word searches

The updated search now treats multiple words as separate search terms.

Instead of searching for the full phrase exactly, it looks for the **intersection** of the words on the **same result**.

### Example
Search:
`Confluence CLN`

The search splits that into:
- `Confluence`
- `CLN`

Then it checks whether a result matches **both** terms somewhere in that same result.

So a request type can still match even if:
- `Confluence` matches the **request type name**
- `CLN` matches the **project key**

That means users can combine:
- a request type name
- a project key
- a portal name
- or a keyword from a description

in one search.

---

## Keyword searches

If a portal’s project description contains helpful keywords, users can find that portal by searching those words.

### Example
If a project description includes:
- Atlassian
- Jira
- Confluence

then users can find that portal even if those words are not part of the project name.

This makes it easier to support discovery based on what the portal is for, not just what it is called.

---

## Typo tolerance

A proof-of-concept typo-tolerant fallback has also been added.

### What that means
The search first tries the normal matching logic.

If nothing is found, it can do a softer fallback pass to try to recover from some misspellings.

### Important note
This is **not full spellcheck**. It is a best-effort fallback to help with some misspellings.

That means:
- some misspellings may still work
- some may not
- partial word matches may already work even without fuzzy matching

So typo support should be viewed as a helpful fallback, not a guarantee.

---

## Highlighting in results

Search results highlight matched text to help users understand **why** a result appeared.

### Portal results
Portal results can show:
- **Matched on project name**
- **Matched on project key**
- **Matched on keyword**

### Request type results
Request type results show the matching request type, and matching project information can also be highlighted so users can see how a multi-word query matched.

This is especially useful for searches like:
`Confluence CLN`

where:
- `Confluence` matches the request type name
- `CLN` matches the project key

---

## What users should expect

### Good searches to try
- A portal name  
  Example: `Ask HR`

- A project key  
  Example: `CLN`

- A keyword from a portal description  
  Example: `Jira`

- A request type name  
  Example: `Confluence`

- A combined search  
  Example: `Confluence CLN`

### What combined searches do
A combined search tries to find results where all words connect to the same result, even if they match different fields.

---

## Summary

The updated search is meant to be:

- **broader** — searches both portals and request types
- **smarter** — supports multi-word intersection across fields
- **better ranked** — prioritizes portal identity first
- **more discoverable** — supports description-based keyword matching
- **more forgiving** — includes a typo-tolerant fallback proof of concept

This should make it easier for users to find the correct portal or request type without already knowing the exact wording.