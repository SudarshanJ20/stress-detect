"""Export the temporal model to ONNX with SPEC_VERSION + input-contract metadata, and verify
PyTorch and ONNX Runtime agree. The input contract is what Phase-5 Kotlin must construct;
an undocumented shape is where parity silently breaks, so it is embedded in the file too.
"""
from __future__ import annotations

import numpy as np
import onnx
import onnxruntime as ort
import torch


def export_and_verify(model, d_dyn: int, d_static: int, path: str, spec_version: str,
                      atol: float = 1e-5):
    model.eval()
    seq = torch.randn(1, 7, d_dyn); static = torch.randn(1, d_static)
    torch.onnx.export(
        model, (seq, static), path,
        input_names=["seq", "static"], output_names=["stress"],
        dynamic_axes={"seq": {0: "batch"}, "static": {0: "batch"}, "stress": {0: "batch"}},
        opset_version=17,
    )
    contract = f"seq:(B,7,{d_dyn}) float32; static:(B,{d_static}) float32; output stress:(B,)"
    m = onnx.load(path)
    for k, v in (("spec_version", spec_version), ("input_contract", contract)):
        e = m.metadata_props.add(); e.key = k; e.value = v
    onnx.save(m, path)

    sess = ort.InferenceSession(path)
    with torch.no_grad():
        t_out = model(seq, static).numpy().reshape(-1)
    o_out = sess.run(None, {"seq": seq.numpy(), "static": static.numpy()})[0].reshape(-1)
    max_diff = float(np.max(np.abs(t_out - o_out)))
    return {"max_abs_diff": max_diff, "match": bool(np.allclose(t_out, o_out, atol=atol)),
            "contract": contract, "spec_version": spec_version}
