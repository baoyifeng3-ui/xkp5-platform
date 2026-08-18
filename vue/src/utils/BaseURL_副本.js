let baseURL = "/api/"

if (process.env.NODE_ENV == 'production') {
    baseURL = `${location.protocol}//${location.hostname}:18010/`
}

export { baseURL }
