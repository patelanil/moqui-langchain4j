# moqui-langchain4j

[![license](http://img.shields.io/badge/license-CC0%201.0%20Universal-blue.svg)](https://github.com/patelanil/moqui-langchain4j/blob/master/LICENSE.md)

Moqui tool component that integrates LangChain4j for LLM/AI access via `ec.getTool("AI", ...)`.

## What this component does

Registers `AiToolFactory` as a Moqui `ToolFactory` using the standard component plugin pattern —
no framework changes required. Once installed, any Groovy service or script can call
`ec.getTool("AI", AiToolFactory.class)` to send chat messages to an LLM provider and get back
plain text or structured JSON output.

## Prerequisites

- Moqui Framework 4.0+
- Java 21
- An API key from OpenAI or a compatible provider

## Installation

Clone or copy this repository into your Moqui runtime component directory:

    $ cd runtime/component
    $ git clone https://github.com/patelanil/moqui-langchain4j.git

Moqui picks up the component automatically on the next startup. No additional registration is needed;
`MoquiConf.xml` in this component registers the tool factory and is merged at runtime.

## Configuration

Properties are resolved in this order: JVM system property → environment variable → default.

| Property | Default | Description |
|---|---|---|
| `ai_provider` | `openai` | Provider to use. Currently supported: `openai`. |
| `ai_model` | `gpt-4o-mini` | Model name passed to the provider. |
| `ai_api_key` | *(none)* | **Required.** API key for the provider. |
| `ai_base_url` | *(none)* | Override the provider base URL (see Multiple Providers below). |
| `ai_timeout` | `60` | Request timeout in seconds. |

**Via environment variable** (recommended for secrets):

    export AI_API_KEY=sk-...
    export AI_MODEL=gpt-4o

**Via `MoquiConf.xml`** override in your runtime conf file:

```xml
<default-property name="ai_provider" value="openai"/>
<default-property name="ai_model" value="gpt-4o"/>
<default-property name="ai_api_key" value="${AI_API_KEY}"/>
<default-property name="ai_timeout" value="30"/>
```

## Multiple providers

Setting `ai_base_url` redirects the OpenAI-compatible HTTP client to any endpoint that speaks
the OpenAI API, including:

| Provider | Example base URL |
|---|---|
| Ollama | `http://localhost:11434/v1` |
| Groq | `https://api.groq.com/openai/v1` |
| Together AI | `https://api.together.xyz/v1` |
| Azure OpenAI | `https://<your-resource>.openai.azure.com/openai/deployments/<deployment>` |

Set `ai_provider=openai` with the appropriate `ai_base_url` and `ai_api_key` for the target service.

## Usage

**Free-form text generation:**

```groovy
import org.moqui.ai.AiToolFactory

def ai = ec.getTool("AI", AiToolFactory.class)

def messages = [
    [role: "system",  content: "You are a helpful assistant."],
    [role: "user",    content: "Summarise this order in one sentence: ${orderText}"]
]
String summary = ai.generate(messages)
```

**Structured output** (returns a `Map` matching the schema):

```groovy
import org.moqui.ai.AiToolFactory

def ai = ec.getTool("AI", AiToolFactory.class)

def messages = [
    [role: "user", content: "Extract the customer name and total from: ${invoiceText}"]
]
def schema = [
    customerName: [type: "string"],
    total:        [type: "number"]
]
Map result = ai.generateStructured(messages, schema)
// result.customerName, result.total
```

Schema supports types: `string`, `number`, `integer`, `boolean`, `array`, `object`.

## Running the smoke test

Requires `ai_api_key` to be configured (see Configuration above).

    export AI_API_KEY=sk-...
    ./gradlew :runtime:component:moqui-langchain4j:testAi

The test boots the full Moqui ECF, calls both `generate()` and `generateStructured()` against the
live provider, and asserts non-empty responses.

## Adding new providers

To support a new provider, add a `case` to the `switch (provider)` block in
[`src/main/groovy/org/moqui/ai/AiToolFactory.groovy`](src/main/groovy/org/moqui/ai/AiToolFactory.groovy).
Build a `ChatModel` using the appropriate LangChain4j module (e.g. `langchain4j-anthropic`,
`langchain4j-google-ai-gemini`) and assign it to `this.chatModel`. Add the new module to
`build.gradle` dependencies and set `ai_provider` to your new case value.
