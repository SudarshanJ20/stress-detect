"""Export the temporal model to ONNX with SPEC_VERSION + input-contract metadata, and verify
PyTorch and ONNX Runtime agree. The input contract is what Phase-5 Kotlin must construct;
an undocumented shape is where parity silently breaks, so it is embedded in the file too.
"""
from __future__ import annotations

import json

import numpy as np
import onnx
import onnxruntime as ort
import torch


def export_and_verify(model, d_dyn: int, d_static: int, path: str, spec_version: str,
                      atol: float = 1e-5, scaler=None, dyn_features=None,
                      static_features=None):
    """Export + verify. `scaler` is the (dyn_mean, dyn_sd, static_mean, static_sd) tuple
    from `_fit_scaler`; it is embedded in the ONNX metadata because the model consumes
    STANDARDIZED inputs and the app has no other way to reproduce that transform. Shipping
    the model without it is a silent-wrong-answer bug: raw features would be scored as if
    they were z-scores."""
    model.eval()
    seq = torch.randn(1, 7, d_dyn); static = torch.randn(1, d_static)
    torch.onnx.export(
        model, (seq, static), path,
        input_names=["seq", "static"], output_names=["stress"],
        dynamic_axes={"seq": {0: "batch"}, "static": {0: "batch"}, "stress": {0: "batch"}},
        opset_version=17,
    )
    contract = f"seq:(B,7,{d_dyn}) float32; static:(B,{d_static}) float32; output stress:(B,)"
    meta = {"spec_version": spec_version, "input_contract": contract}
    if scaler is not None:
        dm, ds, sm, ss = scaler
        meta["standardization"] = json.dumps({
            "dyn_mean": [float(x) for x in dm], "dyn_sd": [float(x) for x in ds],
            "static_mean": [float(x) for x in sm], "static_sd": [float(x) for x in ss],
            # Order of application matters: standardize FIRST, then NaN → 0, so 0 means
            # "the training mean", not "zero hours".
            "order": "standardize_then_nan_to_zero",
        })
    if dyn_features is not None:
        meta["dyn_features"] = json.dumps(list(dyn_features))
    if static_features is not None:
        meta["static_features"] = json.dumps(list(static_features))

    m = onnx.load(path)
    for k, v in meta.items():
        e = m.metadata_props.add(); e.key = k; e.value = v
    onnx.save(m, path)

    sess = ort.InferenceSession(path)
    with torch.no_grad():
        t_out = model(seq, static).numpy().reshape(-1)
    o_out = sess.run(None, {"seq": seq.numpy(), "static": static.numpy()})[0].reshape(-1)
    max_diff = float(np.max(np.abs(t_out - o_out)))
    return {"max_abs_diff": max_diff, "match": bool(np.allclose(t_out, o_out, atol=atol)),
            "contract": contract, "spec_version": spec_version}
