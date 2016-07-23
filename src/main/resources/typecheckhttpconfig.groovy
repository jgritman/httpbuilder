unresolvedVariable { var ->
    if('request' == var.name) {
        storeType(var, classNodeFor(groovyx.net.http.HttpConfig.Request))
        handled = true
    }

    if('response' == var.name) {
        storeType(var, classNodeFor(groovyx.net.http.HttpConfig.Response))
        handled = true
    }
}
