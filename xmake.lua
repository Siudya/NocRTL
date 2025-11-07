
task("rtl", function()
    set_menu {
        usage = "xmake rtl [options]",
        description = "Generate noc rtl",
        options = {
            {'t', "topology", "kv", "mesh", "Which config will be used."}
        }
    }

    on_run(function()
        import("core.base.option")
        local run_opts = {"run"}
        table.join2(run_opts, {"--topology", option.get("topology") })
        table.join2(run_opts, {"-td", "build", "--throw-on-first-error", "--target", "systemverilog", "--full-stacktrace"})
        if os.host() == "windows" then
            os.execv(os.shell(), table.join({ "mill" }, run_opts))
        else
            os.execv("mill", run_opts)
        end
    end)
end)

task("idea", function()
    on_run(function()
        if os.host() == "windows" then
            os.execv(os.shell(), { "mill", "-i", "mill.idea.GenIdea/idea" })
        else
            os.execv("mill", { "-i", "mill.idea.GenIdea/idea" })
        end
    end)
    set_menu {
        options = {}
    }
end)

task("comp", function()
    on_run(function()
        if os.host() == "windows" then
            os.execv(os.shell(), { "mill", "-i", "compile" })
            os.execv(os.shell(), { "mill", "-i", "test.compile" })
        else
            os.execv("mill", { "-i", "compile" })
            os.execv("mill", { "-i", "test.compile" })
        end
    end)
    set_menu {
        options = {}
    }
end)

task("init", function()
    on_run(function()
        os.cd(os.scriptdir())
        os.exec("git submodule update --init")
    end)
    set_menu {
        options = {} 
    }
end)