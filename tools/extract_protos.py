"""Rebuild .proto sources from the compiled *_pb2.py files of mbapi2020 (MIT).

Usage: python3 -I tools/extract_protos.py <reference proto dir> <output dir>

The serialized FileDescriptorProto is read straight out of each _pb2.py
(AddSerializedFile(b'...')) without importing the module. gogo.proto and all
gogo options are dropped: they only affect Go code generation.
"""
import ast
import os
import re

import sys

from google.protobuf import descriptor_pb2 as d

JAVA_PACKAGE = "pt.sergiotaveira.mercedes.proto"
SKIP_FILES = {"gogo.proto"}

TYPES = {
    d.FieldDescriptorProto.TYPE_DOUBLE: "double", d.FieldDescriptorProto.TYPE_FLOAT: "float",
    d.FieldDescriptorProto.TYPE_INT64: "int64", d.FieldDescriptorProto.TYPE_UINT64: "uint64",
    d.FieldDescriptorProto.TYPE_INT32: "int32", d.FieldDescriptorProto.TYPE_FIXED64: "fixed64",
    d.FieldDescriptorProto.TYPE_FIXED32: "fixed32", d.FieldDescriptorProto.TYPE_BOOL: "bool",
    d.FieldDescriptorProto.TYPE_STRING: "string", d.FieldDescriptorProto.TYPE_BYTES: "bytes",
    d.FieldDescriptorProto.TYPE_UINT32: "uint32", d.FieldDescriptorProto.TYPE_SFIXED32: "sfixed32",
    d.FieldDescriptorProto.TYPE_SFIXED64: "sfixed64", d.FieldDescriptorProto.TYPE_SINT32: "sint32",
    d.FieldDescriptorProto.TYPE_SINT64: "sint64",
}


def read_descriptor(path: str) -> d.FileDescriptorProto:
    tree = ast.parse(open(path, encoding="utf-8").read())
    for node in ast.walk(tree):
        if isinstance(node, ast.Call) and getattr(node.func, "attr", "") == "AddSerializedFile":
            fd = d.FileDescriptorProto()
            fd.ParseFromString(ast.literal_eval(node.args[0]))
            return fd
    raise ValueError(f"no serialized descriptor in {path}")


def type_name(f: d.FieldDescriptorProto) -> str:
    if f.type in (d.FieldDescriptorProto.TYPE_MESSAGE, d.FieldDescriptorProto.TYPE_ENUM, d.FieldDescriptorProto.TYPE_GROUP):
        return f.type_name  # fully qualified, leading dot -> valid in .proto
    return TYPES[f.type]


def emit_enum(e, ind, out):
    out.append(f"{ind}enum {e.name} {{")
    if e.options.HasField("allow_alias") and e.options.allow_alias:
        out.append(f"{ind}  option allow_alias = true;")
    for v in e.value:
        out.append(f"{ind}  {v.name} = {v.number};")
    out.append(f"{ind}}}")


def field_line(f, syntax, msg_by_name, in_oneof):
    t = type_name(f)
    if f.type == d.FieldDescriptorProto.TYPE_MESSAGE and f.label == d.FieldDescriptorProto.LABEL_REPEATED:
        entry = msg_by_name.get(f.type_name)
        if entry is not None and entry.options.map_entry:
            k, v = entry.field[0], entry.field[1]
            return f"map<{type_name(k)}, {type_name(v)}> {f.name} = {f.number};"
    label = ""
    if f.label == d.FieldDescriptorProto.LABEL_REPEATED:
        label = "repeated "
    elif syntax == "proto2" and not in_oneof:
        label = "required " if f.label == d.FieldDescriptorProto.LABEL_REQUIRED else "optional "
    elif f.proto3_optional:
        label = "optional "
    opts = []
    if f.options.HasField("packed"):
        opts.append(f"packed = {'true' if f.options.packed else 'false'}")
    if f.HasField("default_value"):
        dv = f.default_value
        if f.type == d.FieldDescriptorProto.TYPE_STRING:
            dv = '"' + dv.replace('"', '\\"') + '"'
        opts.append(f"default = {dv}")
    if f.HasField("json_name") and syntax == "proto2":
        pass
    o = f" [{', '.join(opts)}]" if opts else ""
    return f"{label}{t} {f.name} = {f.number}{o};"


def emit_message(m, ind, out, syntax, msg_by_name):
    if m.options.map_entry:
        return
    out.append(f"{ind}message {m.name} {{")
    for e in m.enum_type:
        emit_enum(e, ind + "  ", out)
    for n in m.nested_type:
        emit_message(n, ind + "  ", out, syntax, msg_by_name)
    for r in m.reserved_range:
        out.append(f"{ind}  reserved {r.start} to {r.end - 1};" if r.end - 1 > r.start else f"{ind}  reserved {r.start};")
    real_oneofs = {}
    for f in m.field:
        if f.HasField("oneof_index") and not f.proto3_optional:
            real_oneofs.setdefault(f.oneof_index, []).append(f)
    done = set()
    for f in m.field:
        if f.HasField("oneof_index") and not f.proto3_optional:
            idx = f.oneof_index
            if idx in done:
                continue
            done.add(idx)
            out.append(f"{ind}  oneof {m.oneof_decl[idx].name} {{")
            for of in real_oneofs[idx]:
                out.append(f"{ind}    {field_line(of, syntax, msg_by_name, True)}")
            out.append(f"{ind}  }}")
        else:
            out.append(f"{ind}  {field_line(f, syntax, msg_by_name, False)}")
    out.append(f"{ind}}}")


def index_messages(fd):
    by = {}

    def walk(prefix, msgs):
        for m in msgs:
            full = f"{prefix}.{m.name}"
            by[full] = m
            walk(full, m.nested_type)

    walk("." + fd.package if fd.package else "", fd.message_type)
    return by


def render(fd: d.FileDescriptorProto, all_by_name) -> str:
    syntax = fd.syntax or "proto2"
    out = [f'syntax = "{syntax}";', ""]
    if fd.package:
        out.append(f"package {fd.package};")
    out.append(f'option java_package = "{JAVA_PACKAGE}";')
    outer = "".join(p.capitalize() for p in re.split(r"[-_]", os.path.splitext(os.path.basename(fd.name))[0])) + "Proto"
    out.append(f'option java_outer_classname = "{outer}";')
    out.append("option java_multiple_files = true;")
    out.append("")
    for dep in fd.dependency:
        if os.path.basename(dep) in SKIP_FILES:
            continue
        out.append(f'import "{dep if dep.startswith("google/") else os.path.basename(dep)}";')
    out.append("")
    for e in fd.enum_type:
        emit_enum(e, "", out)
    for m in fd.message_type:
        emit_message(m, "", out, syntax, all_by_name)
    out.append("")
    return "\n".join(out)


def main(src_dir: str, out_dir: str) -> None:
    os.makedirs(out_dir, exist_ok=True)
    fds = [read_descriptor(os.path.join(src_dir, f)) for f in sorted(os.listdir(src_dir)) if f.endswith("_pb2.py")]
    all_by_name = {}
    for fd in fds:
        all_by_name.update(index_messages(fd))
    for fd in fds:
        name = os.path.basename(fd.name)
        if name in SKIP_FILES:
            continue
        with open(os.path.join(out_dir, name), "w", encoding="utf-8") as fh:
            fh.write(render(fd, all_by_name))
        print("wrote", name)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
