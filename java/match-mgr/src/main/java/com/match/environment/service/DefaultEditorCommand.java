package com.match.environment.service;

import java.util.Arrays;
import java.util.List;

final class DefaultEditorCommand {
    private DefaultEditorCommand() {}

    static List<String> value() {
        String mirror = "https://mirrors.tuna.tsinghua.edu.cn/anaconda";
        String command = "m=" + mirror + ";printf 'channels:\n- defaults\ndefault_channels:\n- " + mirror
                + "/pkgs/main\n- " + mirror + "/pkgs/r\n- " + mirror + "/pkgs/msys2\ncustom_channels:\n  conda-forge: "
                + mirror + "/cloud\n'>/root/.condarc;rm -rf /root/.conda/pkgs/cache;"
                + "b(){ s=/usr/local/lib/python3.8/dist-packages;for e in /root/anaconda3/envs/*/lib/python*/site-packages;do "
                + "[ ! -d $e ]||{ p=${e%%/lib/*}/bin/python;[ ! -x $p ]||$p -c 'import tensorflow as t;"
                + "assert t.__version__.startswith(\"1.15\")' >/dev/null 2>&1&&{ ln -sfn $s/object_detection "
                + "$e/object_detection;ln -sfn $s/slim $e/slim;};};done;};b;(while sleep 10;do b;done)&"
                + "code-server --bind-addr 0.0.0.0:9090 --disable-telemetry --disable-update-check "
                + "--disable-workspace-trust --cert /root/.config/code-cert.pem --cert-key /root/.config/code-cert-key.pem "
                + "/home/student/data >/tmp/cs.log 2>&1&"
                + "(until wget -q --no-check-certificate -O/dev/null https://127.0.0.1:9090;do sleep .2;done)&"
                + "PYTHONPATH=/usr/local/zy-T100/utils_x86/models/A:/usr/local/zy-T100/utils_x86/models/B "
                + "nohup bash /usr/local/zy-T100/bin/start.sh >/tmp/t100.log 2>&1&"
                + "nohup jupyter-lab --ip=0.0.0.0 --port=8888 --allow-root --no-browser --ServerApp.token= "
                + "--ServerApp.password= --ServerApp.root_dir=/home/student/data --ServerApp.default_url=/lab "
                + ">/tmp/jupyter.log 2>&1&exec tail -f /dev/null";
        return Arrays.asList("/bin/sh", "-c", command);
    }
}
