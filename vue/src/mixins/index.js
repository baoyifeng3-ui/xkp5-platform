const mixins = {
    methods: {
        addZero(num) {
            return num < 10 ? '0' + num : num
        },
        handleDate(date) {
            let dt = new Date(date);
            return `${dt.getFullYear()}/${dt.getMonth() + 1
                }/${dt.getDate()} ${this.addZero(dt.getHours())}:${this.addZero(dt.getMinutes())}:${this.addZero(dt.getSeconds())}`;
        }
    }
}

export default mixins