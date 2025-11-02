
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