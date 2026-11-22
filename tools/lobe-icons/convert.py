#!/usr/bin/env python3
"""Turn LobeHub's AI brand SVGs into Android VectorDrawables, and emit the Kotlin tables.

Source: https://icons.lobehub.com/ (package `@lobehub/icons-static-svg`, MIT licensed).
The *plain* icons are the single-colour variant, drawn in `currentColor`, which is what an Android
tintable glyph wants, so `-color` and `-text` variants are deliberately not used.

Run from the repository root:  python3 tools/lobe-icons/convert.py
It writes `zyn-chat-app/src/main/res/drawable/ic_model_<id>.xml` and the two generated Kotlin files.

The conversion is a source translation of MIT-licensed artwork. Android VectorDrawable has no
`<use>`, no CSS and no gradients here (none of the selected icons contain them), and `evenOdd` is
supported from API 24, so the app's minSdk of 26 is safe.
"""

import pathlib
import re
import sys
import urllib.request

VERSION = "1.104.0"
BASE = f"https://unpkg.com/@lobehub/icons-static-svg@{VERSION}/icons"

# id | lobehub slug | displayed name | words that identify the brand
#
# A brand's own id works as an identifying word automatically, so `tokens` only lists the names a
# brand is known by that differ from it: model families (`llama` for Meta) and provider aliases
# (`bedrock` for Amazon). Brand ids must match `ic_model_<id>.xml` and the Kotlin `id` field.
BRANDS = """
openai|openai|OpenAI|gpt,chatgpt,o1,o3,o4,davinci,codex,sora
anthropic|anthropic|Anthropic|claude,haiku,sonnet,opus
google|google|Google|gemini,gemma,palm,bison,deepmind,vertex
meta|meta|Meta|llama
microsoft|microsoft|Microsoft|phi,copilot,azure
mistral|mistral|Mistral AI|mixtral,codestral,pixtral,magistral,devstral,ministral,voxtral
deepseek|deepseek|DeepSeek|deepseek
qwen|qwen|Qwen|qwq,qvq,tongyi,alibaba
xai|xai|xAI|grok
cohere|cohere|Cohere|command,aya
perplexity|perplexity|Perplexity|sonar
nvidia|nvidia|NVIDIA|nemotron
amazon|aws|Amazon|nova,titan,bedrock
ai2|ai2|AI2|olmo
moonshot|moonshot|Moonshot AI|kimi
zhipu|zhipu|Zhipu AI|glm,chatglm,zai
baidu|baidu|Baidu|ernie,wenxin
tencent|tencent|Tencent|hunyuan,yuanbao
bytedance|bytedance|ByteDance|doubao,volcengine,jimeng,seed
minimax|minimax|MiniMax|abab,hailuo
zeroone|zeroone|01.AI|yi
stepfun|stepfun|StepFun|step
ai21|ai21|AI21|jamba
baichuan|baichuan|Baichuan|baichuan
inflection|inflection|Inflection|pi
reka|reka|Reka|reka
dolphin|dolphin|Dolphin|dolphin
llava|llava|LLaVA|llava
rwkv|rwkv|RWKV|rwkv
dbrx|dbrx|DBRX|dbrx
tii|tii|TII|falcon
internlm|internlm|InternLM|internlm
huawei|huawei|Huawei|pan
spark|spark|iFlytek Spark|iflytek
sensenova|sensenova|SenseNova|sensenova
longcat|longcat|LongCat|longcat
baai|baai|BAAI|bge
tiangong|tiangong|Tiangong|tiangong
openrouter|openrouter|OpenRouter|openrouter
groq|groq|Groq|groq
together|together|Together AI|together
fireworks|fireworks|Fireworks AI|fireworks
cerebras|cerebras|Cerebras|cerebras
sambanova|sambanova|SambaNova|sambanova
deepinfra|deepinfra|DeepInfra|deepinfra
novita|novita|Novita AI|novita
hyperbolic|hyperbolic|Hyperbolic|hyperbolic
cloudflare|cloudflare|Cloudflare|workersai
huggingface|huggingface|Hugging Face|hf
replicate|replicate|Replicate|replicate
ollama|ollama|Ollama|ollama
lmstudio|lmstudio|LM Studio|lmstudio
vllm|vllm|vLLM|vllm
ibm|ibm|IBM|granite,watsonx
snowflake|snowflake|Snowflake|snowflake
voyage|voyage|Voyage AI|voyage
jina|jina|Jina AI|jina
nebius|nebius|Nebius|nebius
baseten|baseten|Baseten|baseten
anyscale|anyscale|Anyscale|anyscale
leptonai|leptonai|Lepton AI|lepton
lambda|lambda|Lambda|lambda
crusoe|crusoe|Crusoe|crusoe
pollinations|pollinations|Pollinations|pollinations
siliconcloud|siliconcloud|SiliconFlow|siliconflow
modelscope|modelscope|ModelScope|modelscope
upstage|upstage|Upstage|solar
nousresearch|nousresearch|Nous Research|hermes,nous
featherless|featherless|Featherless|featherless
chutes|chutes|Chutes|chutes
parasail|parasail|Parasail|parasail
unsloth|unsloth|Unsloth|unsloth
phind|phind|Phind|phind
venice|venice|Venice|venice
poe|poe|Poe|poe
deepai|deepai|DeepAI|deepai
giteeai|giteeai|Gitee AI|gitee
qiniu|qiniu|Qiniu|qiniu
statecloud|statecloud|State Cloud|statecloud
ppio|ppio|PPIO|ppio
infinigence|infinigence|Infinigence|infinigence
aimlapi|aimlapi|AI/ML API|aimlapi
vercel|vercel|Vercel|vercel
stability|stability|Stability AI|stablediffusion,sdxl
midjourney|midjourney|Midjourney|midjourney
flux|flux|FLUX|bfl
runway|runway|Runway|runway
luma|luma|Luma|dreammachine
kling|kling|Kling|kling
ideogram|ideogram|Ideogram|ideogram
elevenlabs|elevenlabs|ElevenLabs|elevenlabs
suno|suno|Suno|suno
udio|udio|Udio|udio
recraft|recraft|Recraft|recraft
pika|pika|Pika|pika
exa|exa|Exa|exa
tavily|tavily|Tavily|tavily
firecrawl|firecrawl|Firecrawl|firecrawl
brave|brave|Brave Search|brave
kagi|kagi|Kagi|kagi
""".strip()

HEADER = """/*
 * Copyright (c) 2014-2026 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://github.com/GetStream/stream-chat-android-ai/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
"""

TAG_RE = re.compile(r"<([a-zA-Z]+)")
ATTR_RE = re.compile(r"([a-zA-Z-]+)=\"([^\"]*)\"")
PATH_RE = re.compile(r"<path\b([^>]*?)/?>")
# Elements a VectorDrawable cannot express as-is. None of the selected icons use them; if one ever
# does, failing here is better than shipping a silently wrong mark.
UNSUPPORTED = {
    "mask",
    "clipPath",
    "use",
    "style",
    "image",
    "text",
    "linearGradient",
    "radialGradient",
    "pattern",
}

DRAWABLE_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!--
  {name} mark. Generated by tools/lobe-icons/convert.py from the MIT-licensed
  LobeHub icon set (https://icons.lobehub.com/); do not edit by hand.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
{paths}</vector>
"""


def parse_brands() -> list[dict[str, object]]:
    """Reads the brand table above into records."""
    brands = []
    for line in BRANDS.splitlines():
        brand_id, slug, display, tokens = line.split("|")
        brands.append(
            {
                "id": brand_id,
                "slug": slug,
                "display": display,
                "tokens": [token for token in tokens.split(",") if token],
            }
        )
    return brands


def fetch(slug: str) -> str:
    """Downloads one source SVG."""
    with urllib.request.urlopen(f"{BASE}/{slug}.svg", timeout=30) as response:
        return response.read().decode("utf-8")


def to_colour(value: str) -> str | None:
    """`#rgb`/`#rrggbb` to `#aarrggbb`, or None when the paint is inheritance rather than a colour."""
    value = value.strip()
    if value in ("none", "currentColor", "transparent", ""):
        return None
    if value.startswith("#"):
        digits = value[1:]
        if len(digits) == 3:
            digits = "".join(char * 2 for char in digits)
        if len(digits) == 6:
            return f"#ff{digits.lower()}"
    return None


def path_xml(attrs: dict[str, str]) -> str:
    """One `<path>`, or an empty string if it cannot be expressed as a VectorDrawable."""
    data = attrs.get("d")
    if not data:
        return ""
    fill = to_colour(attrs.get("fill", "currentColor"))
    attributes = []
    if attrs.get("fill-rule") == "evenodd":
        attributes.append('android:fillType="evenOdd"')
    if fill:
        attributes.append(f'android:fillColor="{fill}"')
    else:
        # The artwork relies on `currentColor`, so the tint has to supply the colour.
        attributes.append("android:fillColor=\"#ff000000\"")
    joined = "\n        ".join(attributes)
    return f'    <path\n        android:pathData="{data}"\n        {joined} />\n'


def convert(drawable: str, slug: str) -> tuple[str, int]:
    """Converts one fetched SVG into a VectorDrawable, returning it and the path count."""
    unsupported = sorted({name for name in TAG_RE.findall(drawable) if name in UNSUPPORTED})
    if unsupported:
        raise ValueError(f"{slug}: uses {', '.join(unsupported)}")

    paths = [
        path_xml(dict(ATTR_RE.findall(attributes)))
        for attributes in PATH_RE.findall(drawable)
    ]
    paths = [path for path in paths if path]
    if not paths:
        raise ValueError(f"{slug}: no drawable paths")
    return DRAWABLE_TEMPLATE.format(name=slug, paths="".join(paths)), len(paths)


def write_drawable(root: pathlib.Path, brand: dict[str, object]) -> int:
    """Fetches, converts and writes one drawable. Returns its path count."""
    drawable, count = convert(fetch(str(brand["slug"])), str(brand["id"]))
    target = root / f"ic_model_{brand['id']}.xml"
    target.write_text(drawable, encoding="utf-8")
    return count


def logo_entries(brands: list[dict[str, object]]) -> str:
    """The `BrandLogo` list for the generated Kotlin catalogue."""
    entries = []
    for brand in brands:
        tokens = ", ".join(f'"{token}"' for token in brand["tokens"])
        entries.append(
            "    BrandLogo(\n"
            f'        id = "{brand["id"]}",\n'
            f'        displayName = "{brand["display"]}",\n'
            f"        tokens = listOf({tokens}),\n"
            "    ),"
        )
    return "\n".join(entries)


def kotlin_catalog(brands: list[dict[str, object]]) -> str:
    """The generated brand catalogue."""
    return f"""{HEADER}
package com.zyn.chat.app.data.providers

/**
 * The brands the app can draw a mark for.
 *
 * **Generated by `tools/lobe-icons/convert.py` — do not edit by hand.** Each `id` matches a
 * `res/drawable/ic_model_<id>.xml` produced by the same script, and `tokens` lists the words that
 * identify the brand in a model slug or a provider id. Adding a brand means adding a row to the
 * script's table and re-running it.
 *
 * This is recognition, not a model list: nothing here claims a model exists or is available.
 */
public object BrandCatalog {{

    /** Every known brand, in the order the script lists them. */
    public val all: List<BrandLogo> = listOf(
{logo_entries(brands)}
    )
}}

/**
 * One brand the app knows.
 *
 * @param id Stable identifier, also the drawable's file name after `ic_model_`.
 * @param displayName Name shown to people.
 * @param tokens Words that identify the brand; the id is matched as well.
 */
public data class BrandLogo(
    public val id: String,
    public val displayName: String,
    public val tokens: List<String>,
)
"""


def kotlin_marks(brands: list[dict[str, object]]) -> str:
    """The generated brand-to-drawable mapping."""
    arms = "\n".join(
        f'        "{brand["id"]}" -> R.drawable.ic_model_{brand["id"]}' for brand in brands
    )
    return f"""{HEADER}
package com.zyn.chat.app.ui.brand

import androidx.annotation.DrawableRes
import com.zyn.chat.app.R
import com.zyn.chat.app.data.providers.ModelBrands

/**
 * Drawable for a brand, or `null` for a brand with no artwork, which the UI shows initials for.
 *
 * **Generated by `tools/lobe-icons/convert.py` — do not edit by hand.** The artwork itself comes from
 * the MIT-licensed LobeHub icon set (<https://icons.lobehub.com/>) and each drawable is tintable, so
 * marks follow the theme rather than carrying fixed brand colours.
 *
 * @param brandId A [com.zyn.chat.app.data.providers.BrandLogo] id.
 */
@DrawableRes
public fun markOf(brandId: String): Int? = when (brandId) {{
{arms}
        else -> null
    }}

/**
 * Drawable for whoever makes a model, or `null` when it is not recognised and initials should be
 * shown instead.
 *
 * @param providerId Id of the provider offering the model.
 * @param slug Provider-side model identifier, or empty to brand the provider itself.
 */
@DrawableRes
public fun markFor(providerId: String, slug: String): Int? =
    ModelBrands.of(providerId = providerId, slug = slug)?.let {{ brand -> markOf(brand.id) }}
"""


def main() -> int:
    """Regenerates the drawables, the catalogue and the drawable mapping."""
    root = pathlib.Path(__file__).resolve().parents[2]
    drawables = root / "zyn-chat-app/src/main/res/drawable"
    brands = parse_brands()

    if len({brand["id"] for brand in brands}) != len(brands):
        print("duplicate brand id in the table", file=sys.stderr)
        return 1

    for brand in brands:
        try:
            count = write_drawable(drawables, brand)
        except Exception as error:  # noqa: BLE001 - report and stop on the first bad row
            print(f"failed {brand['id']}: {error}", file=sys.stderr)
            return 1
        print(f"{brand['id']:>14}  {count} path(s)")

    catalogue = root / (
        "zyn-chat-app/src/main/kotlin/com/zyn/chat/app/data/providers/BrandCatalog.kt"
    )
    catalogue.write_text(kotlin_catalog(brands), encoding="utf-8")
    marks = root / "zyn-chat-app/src/main/kotlin/com/zyn/chat/app/ui/brand/ModelMarks.kt"
    marks.write_text(kotlin_marks(brands), encoding="utf-8")

    print(f"\n{len(brands)} brands -> drawables, BrandCatalog.kt, ModelMarks.kt")
    return 0


if __name__ == "__main__":
    sys.exit(main())
