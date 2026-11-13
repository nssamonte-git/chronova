<?php

namespace App\Import;

use App\Voice\ProviderUnavailable;
use Illuminate\Http\Client\Factory as Http;
use Illuminate\Http\Client\Pool;
use Illuminate\Http\Client\Response;

/**
 * Meta Model API, three calls at once:
 * - Muse Spark (Chat Completions, strict JSON schema) reads the chart: text, axis, rows;
 * - SAM 3.1 (Responses) finds the bars, and again the milestone marks: one concept per request.
 *
 * The image goes as a base64 data URL (never a public link); Meta's Standard models don't train on
 * it, and SAM is asked not to store its result. Errors never carry Meta's response body.
 */
final class MetaChartAnalyzer implements ChartAnalyzer
{
    public function __construct(private readonly Http $http, private readonly array $config) {}

    public function analyze(string $image, string $mime, int $width, int $height): ChartAnalysis
    {
        if (blank($this->config['key'] ?? null)) {
            throw new ProviderUnavailable('Meta is not configured.');
        }
        // Standard mode only: Meta may train on what "-contributor" models are sent.
        if (str_ends_with($this->config['reader_model'], '-contributor') || str_ends_with($this->config['segmenter_model'], '-contributor')) {
            throw new ProviderUnavailable('Meta contributor models are not allowed.');
        }
        $dataUrl = 'data:'.$mime.';base64,'.base64_encode($image);
        $base = rtrim($this->config['base_url'], '/');
        $phrases = $this->config['segment_phrases'];

        $responses = $this->http->pool(fn (Pool $pool) => [
            $this->request($pool->as('read'))->post($base.'/chat/completions', $this->readBody($dataUrl, $width, $height)),
            $this->request($pool->as('bars'))->post($base.'/responses', $this->segmentBody($phrases['bar'], $dataUrl)),
            $this->request($pool->as('milestones'))->post($base.'/responses', $this->segmentBody($phrases['milestone'], $dataUrl)),
        ]);
        foreach (['read', 'bars', 'milestones'] as $name) {
            $response = $responses[$name] ?? null;
            if (! $response instanceof Response) {
                throw new ProviderUnavailable('Meta could not be reached.');
            }
            if (! $response->successful()) {
                throw new ProviderUnavailable('Meta answered with HTTP '.$response->status().'.');
            }
        }

        $read = $responses['read']->json();
        $choice = $read['choices'][0] ?? null;
        $reading = is_string($choice['message']['content'] ?? null) ? json_decode($choice['message']['content'], true) : null;
        if (! is_array($reading) || ($choice['finish_reason'] ?? 'stop') !== 'stop') {
            throw new ProviderUnavailable('Meta did not finish reading the chart.');
        }

        $shapes = [];
        foreach (['bars' => 'bar', 'milestones' => 'milestone'] as $name => $kind) {
            $text = SamBoxes::outputText($responses[$name]->json());
            if ($text === null) {
                throw new ProviderUnavailable('Meta did not answer with segments.');
            }
            foreach (SamBoxes::parse($text) as $box) {
                $shapes[] = ['kind' => $kind] + $box;
            }
        }
        // SAM finds the pale tracks behind the bars too; their colour tells them apart.
        foreach (BoxColours::saturations($image, $shapes) as $i => $saturation) {
            $shapes[$i]['saturation'] = $saturation;
        }

        $usage = $read['usage'] ?? [];

        return new ChartAnalysis(
            $reading,
            $shapes,
            $width,
            $height,
            isset($usage['prompt_tokens']) ? (int) $usage['prompt_tokens'] : null,
            isset($usage['completion_tokens']) ? (int) $usage['completion_tokens'] : null,
        );
    }

    private function request(mixed $pending): mixed
    {
        return $pending->withToken($this->config['key'])->acceptJson()->timeout($this->config['timeout']);
    }

    private function readBody(string $dataUrl, int $width, int $height): array
    {
        return [
            'model' => $this->config['reader_model'],
            'messages' => [
                ['role' => 'system', 'content' => ChartReadingSchema::instructions()],
                ['role' => 'user', 'content' => [
                    ['type' => 'text', 'text' => "The image is {$width}×{$height} pixels."],
                    ['type' => 'image_url', 'image_url' => ['url' => $dataUrl]],
                ]],
            ],
            'response_format' => ['type' => 'json_schema', 'json_schema' => [
                'name' => 'chart_reading',
                'strict' => true,
                'schema' => ChartReadingSchema::jsonSchema(),
            ]],
            // A reasoning model: its thinking shares this budget.
            'max_tokens' => 8000,
            'reasoning_effort' => $this->config['reasoning_effort'],
        ];
    }

    private function segmentBody(string $phrase, string $dataUrl): array
    {
        return [
            'model' => $this->config['segmenter_model'],
            'input' => [[
                'type' => 'message',
                'role' => 'user',
                'content' => [
                    ['type' => 'input_text', 'text' => $phrase],
                    ['type' => 'input_image', 'image_url' => $dataUrl],
                ],
            ]],
            // The smallest masks (they're skipped anyway), and nothing kept at Meta.
            'metadata' => ['mask_encoding' => 'one_bit', 'store' => 'false'],
            'stream' => false,
        ];
    }
}
