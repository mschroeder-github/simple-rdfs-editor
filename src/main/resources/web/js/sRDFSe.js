
//helper methods

function deleteSession(uri, event) {
    event.preventDefault();
    
    if(confirm("The session will be closed and your modelled RDFS ontology will be removed from the server.")) {
        var xhr = new XMLHttpRequest();
        xhr.open('DELETE', uri, true);
        xhr.onload = function () {
            location.href = "/?session_closed";
        };
        xhr.send();
    }
}

function getSessionId() {
    return window.location.pathname.split("/").pop();
}

function camelize(str, cap) {
    return str.replace(/(?:^\w|[A-Z]|\b\w)/g, function (word, index) {
        return (!cap && index == 0) ? word.toLowerCase() : word.toUpperCase();
    }).replace(/\s+/g, '');
}

function splitCamelCase(str) {
    return str.replace(/([a-z](?=[A-Z]))/g, '$1 ').toLowerCase();
}

//TODO change to your server address
//or use 'ws://' + location.host + '/websocket'
var wsAddress = 'ws://173.212.240.179:8269/websocket';

//init web socket
Vue.use(VueNativeSock.default, wsAddress, {
    format: 'json',
    reconnection: false,
    reconnectionAttempts: 5,
    reconnectionDelay: 3000,
});

//component to render tree
Vue.component('tree-item', {
    template: '#item-template',
    props: {
        item: Object,
        ontology: Object,
        isRoot: Boolean,
        type: String,
        treeType: String,
        ontologyIndex: Number
    },
    methods: {
        clicked: function (event) {
            event.preventDefault();
            
            if(this.isRoot) {
                if(this.ontologyIndex === 0) {
                    this.$root.newResource(this.type);
                }
            } else {
                this.$root.focusResource(this.item);
            }
        },
        dragstart: function(event) {
            var uri = this.ontology.uri + this.item.localname;
            event.dataTransfer.setData("text/uri-list", uri);
            event.dataTransfer.setData("text/plain", uri);
            event.dataTransfer.setData("source", this.item.hashCode);
            event.dataTransfer.setData("sourceTreeType", this.treeType);
        },
        dragover: function(event) {
            event.preventDefault();
        },
        drop: function(dstTreeType, dst, onto, event) {
            event.preventDefault();
            var srcHashCode = event.dataTransfer.getData("source");
            var sourceTreeType = event.dataTransfer.getData("sourceTreeType");
            this.$root.dragAndDrop(
                    sourceTreeType, srcHashCode, 
                    dstTreeType, dst.hashCode !== undefined ? dst.hashCode : onto.hashCode
            );
        },
        remove: function() {
            if(!this.item.hashCode) {
                //its a ontology
                this.$root.removeResource(this.ontology);
            } else {
                this.$root.removeResource(this.item);
            }
        },
        fulluri: function() {
            return this.ontology.uri + (this.item.localname !== undefined ? this.item.localname : "");
        }
    }
});

//methods for websocket
var wsmethods = {
    init: function(data, vue, $socket) {
        vue.state = data.state;
    },
    created: function(data, vue, $socket) {
        vue.res = data.resource;
        vue.$refs.comment.focus();
    },
    ontology: function(data, vue, $socket) {
        vue.state.ontologies[0].prefix = data.prefix;
        vue.state.ontologies[0].uri = data.uri;
    },
    removed: function(data, vue, $socket) {
        //removed one is currently focused
        if(vue.res.hashCode === data.resource.hashCode) {
            vue.newResource(vue.res.type);
        }
    },
    closed: function(data, vue, $socket) {
        location.href = "/?session_closed";
    }
};

//vue app
var vm = new Vue({
    
    created: function () {
        
        //connection handling
        this.$options.sockets.onopen = (data) => { 
            this.connected = true;
            this.$socket.sendObj({method: 'init', sessionId: getSessionId()});
        };
        this.$options.sockets.onclose = (data) => {
            this.connected = false;
        };
        this.$options.sockets.onerror = (data) => {
            this.connected = false;
            if(this.debug)
                console.log(data);
        };

        //message
        this.$options.sockets.onmessage = (message) => {
            if(this.debug) {
                console.log("S: " + message.data);
            }
            var json = JSON.parse(message.data);
            wsmethods[json.method](json, this, this.$socket);
        };
    },

    data: {
        debug: false,
        connected: false,
        message: 'Hello Vue!',
        state: undefined,
        
        //resource creation or focused
        res: {
            type: "Class",
            localname: "",
            label: {},
            comment: {}
        },
        lang: ""
    },

    methods: {
        //ontology setting change
        prefixChanged: function() {
            this.$socket.sendObj({method: 'prefix', 'value': this.state.ontologies[0].prefix});
        },
        uriChanged: function() {
            this.$socket.sendObj({method: 'uri', 'value': this.state.ontologies[0].uri});
        },
        
        input: function(labelOrLocalname) {
            if(!this.res.hashCode) {
                switch(labelOrLocalname) {
                    case 'label':
                        this.res.localname = encodeURIComponent(camelize(this.res.label[this.lang], this.res.type === 'Class'));
                        break;
                        
                    case 'localname':
                        this.res.label[this.lang] = decodeURIComponent(splitCamelCase(this.res.localname));
                        break;
                }
            }
        },
        
        newResource: function(type, event) {
            if(event) {
                event.preventDefault();
            }
            
            //new
            this.res = {
                type: type,
                localname: "",
                label: {},
                comment: {}
            };
            
            //focus label
            this.$refs.label.focus();
        },
        
        createResource: function() {
            if(!this.res.hashCode) {
                //resource has to be created
                this.$socket.sendObj({ method: 'createResource', 'resource': this.res });
                
                //this.newResource(this.res.type);
            }
        },
        
        focusResource: function(res) {
            this.res = res;
        },
        
        changed: function(what) {
            if(this.res.hashCode) {
                this.$socket.sendObj({ method: 'changed', 'resource': this.res, 'what': what, 'lang': this.lang });
            }
        },
        
        removeResource: function(res) {
            this.$socket.sendObj({ method: 'removeResource', 'resource': res });
        },
        
        reset: function(what, event) {
            if(event) {
                event.preventDefault();
            }
            
            if(this.res.hashCode && this.res.type === 'Property') {
                this.$socket.sendObj({ method: 'reset', 'what': what, hashCode: this.res.hashCode });
            }
        },
        
        importPreset: function(preset, event) {
            if(event) {
                event.preventDefault();
            }
            
            this.$socket.sendObj({ method: 'importPreset', 'preset': preset });
        },
        
        uploadFile: function() {
            this.pushFile('/upload/', this.$refs.uploadFile.files[0]);
        },
        
        importFile: function() {
            this.pushFile('/import/', this.$refs.importFile.files[0]);
        },
        
        pushFile: function(path, file) {
            var data = new FormData();
            data.append('file', file);
            $.ajax({
                url: path + getSessionId(),
                enctype: 'multipart/form-data',
                data: data,
                type: 'POST',
                processData: false,
                contentType: false,
                cache: false
            });
        },
        
        dragAndDrop: function(srcTreeType, srcHashCode, dstTreeType, dstHashCode) {
            this.$socket.sendObj({ method: 'dragAndDrop',
                srcTreeType: srcTreeType,
                srcHashCode: srcHashCode,
                dstTreeType: dstTreeType,
                dstHashCode: dstHashCode
            });
        }
    }
});

