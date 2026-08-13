/* LiteRT dlopens libLiteRtTopKWebGpuSampler.so before using the statically
 * linked WebGPU sampler. On NVIDIA/Vulkan that static Create compiles a
 * shader and SIGILL's in libnvidia-gpucomp (Blackwell). Returning
 * UNAVAILABLE here skips GPU sampling; inference stays on the GPU. */

enum { kUnavailable = 14 };

int LiteRtTopKWebGpuSampler_Create(
    void *env,
    int batch_size,
    int sequence_size,
    int vocab_size,
    const void *activation_data_type,
    const void *sampler_params,
    void **sampler_out,
    char **error_msg
) {
    (void)env;
    (void)batch_size;
    (void)sequence_size;
    (void)vocab_size;
    (void)activation_data_type;
    (void)sampler_params;
    if (sampler_out) *sampler_out = 0;
    if (error_msg) *error_msg = 0;
    return kUnavailable;
}

void LiteRtTopKWebGpuSampler_Destroy(void *sampler) { (void)sampler; }

int LiteRtTopKWebGpuSampler_SampleToIdAndScoreBuffer(
    void *sampler,
    void *logits,
    void *ids,
    const void *scores,
    char **error_msg
) {
    (void)sampler;
    (void)logits;
    (void)ids;
    (void)scores;
    if (error_msg) *error_msg = 0;
    return kUnavailable;
}

int LiteRtTopKWebGpuSampler_UpdateConfig(
    void *sampler,
    const void *params,
    int batch_size,
    void *rand_gen,
    char **error_msg
) {
    (void)sampler;
    (void)params;
    (void)batch_size;
    (void)rand_gen;
    if (error_msg) *error_msg = 0;
    return kUnavailable;
}

int LiteRtTopKWebGpuSampler_CanHandleInput(void *sampler) {
    (void)sampler;
    return 0;
}

int LiteRtTopKWebGpuSampler_HandlesInput(void *sampler) {
    (void)sampler;
    return 0;
}

int LiteRtTopKWebGpuSampler_SetInferenceFuncAndInputTensors(
    void *sampler,
    int (*run_inference)(void *),
    void *arg,
    void *ids,
    void *prev_input_positions,
    void *input_positions,
    void *prev_mask,
    void *mask,
    void *prev_param,
    void *param,
    char **error_msg
) {
    (void)sampler;
    (void)run_inference;
    (void)arg;
    (void)ids;
    (void)prev_input_positions;
    (void)input_positions;
    (void)prev_mask;
    (void)mask;
    (void)prev_param;
    (void)param;
    if (error_msg) *error_msg = 0;
    return kUnavailable;
}
