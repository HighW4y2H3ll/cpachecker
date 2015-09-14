/**
 * Created by magdalena on 06.07.15.
 */
//(function(){})() --> diese Schreibweise macht eine Funktion in JS "self-invoking"
(function() {
    var app = angular.module('report', []);
    app.controller('ReportController', ['$anchorScroll', function($anchorScroll){
        this.date = date;
        this.logo = logo;

        //Tabs in the right Panel
        this.tab = 1;

        //Selected Line in the left Panel --> Errorpath
        this.selected_ErrLine = null;

        //Available Functions (CFA-Graphs)
        this.functions = functions;

        //Selected CFA-Graph
        if (functions.indexOf("main" != -1)) {
            this.selectedCFAFunction = functions.indexOf("main");
        } else {
            this.selectedCFAFunction = 0;
        }


        //preprocess Errorpath-Data for left content
        this.errorPathData = [];
        for(var j = 0; j<errorPathData.length; j++){
            var errPathElem = errorPathData[j];
            if (errPathElem.desc.substring(0, "Return edge from".length) != "Return edge from" && errPathElem.desc != "Function start dummy edge" && errPathElem.desc != "") {
                if(errPathElem.source in fCallEdges){
                    errPathElem.target = fCallEdges[errPathElem.source][0];
                }
                this.errorPathData.push(errPathElem);
            }
        }

        //Behaviour for Click-Elements in Errorpath, CFA, ARG
        this.clickedCFAElement = function($event){
            var y = $event.currentTarget.id;
            if (document.getElementById(y).classList.contains("edge")){
                this.tab = 3;
                var line = cfaInfo["edges"][y.substring("cfa-".length)]["line"];
                this.markSource(line);
            } else if (document.getElementById(y).classList.contains("node") && (y.substring("cfa-".length) > 100000)) {
                var func = document.getElementById(y).getElementsByTagName("text")[0].innerHTML;
                window.alert(func);
                this.selectedCFAFunction = functions.indexOf(func);
            }
        };
        this.clickedARGElement = function($event){
            var y = $event.currentTarget.id;
            if (document.getElementById(y).classList.contains("edge")){
                this.tab = 3;
                var line = document.getElementById(y).getElementsByTagName("text")[0].innerHTML.split(":")[0].substring("Line ".length);
                this.markSource(line);
            } else if (document.getElementById(y).classList.contains("node")){
                var cfaNodeNumber = document.getElementById(y).getElementsByTagName("text")[0].innerHTML.split("N")[1];
                this.markCFANode(cfaNodeNumber);
            }
        };
        this.clickedErrpathElement = function($event){
            var y = $event.currentTarget.id;
            this.setLine(y);
            this.markSource(this.errorPathData[y.substring("errpath-".length)].line);
            this.markCFAedge(y.substring("errpath-".length));
            this.markARGnode(y.substring("errpath-".length));
        };

        this.lineMarked = false;
        this.markSource = function(line){
            if (this.lineMarked) {
                document.getElementsByClassName("markedSourceLine")[0].className = "prettyprint";
            }
            document.getElementById("source-" + line).getElementsByTagName("pre")[1].className = "markedSourceLine";
            $anchorScroll("source-" + line);
            this.lineMarked = true;
        };

        this.cfaEdgeMarked = false;
        this.markCFAedge = function(index){
            var source = this.errorPathData[index].source;
            var target = this.errorPathData[index].target;
            this.selectedCFAFunction = this.functions.indexOf(cfaInfo["nodes"][source]["func"]);
            if(!(source in combinedNodes && target in combinedNodes)) {
                if(source in combinedNodes){
                    source = combinedNodes[source];
                }
                if (this.cfaEdgeMarked) {
                    document.getElementsByClassName("markedCFAEdge")[0].classList.remove("markedCFAEdge");
                }
                document.getElementById("cfa-" + source + "->" + target).classList.add("markedCFAEdge");
                this.cfaEdgeMarked = true;
            }
        };

        this.cfaNodeMarked = false;
        this.markCFANode = function(nodenumber){
            if(this.cfaNodeMarked){
                document.getElementsByClassName("markedCFANode")[0].classList.remove("markedCFANode");
            }
            document.getElementById("cfa-" + nodenumber).classList.add("markedCFANode");
            this.selectedCFAFunction = this.functions.indexOf(cfaInfo["nodes"][nodenumber]["func"]);
            this.tab = 1;
            this.cfaNodeMarked = true;
        };

        this.argNodeMarked = false;
        this.markARGnode = function(index){
            var argElement = this.errorPathData[index].argelem;
            if(this.argNodeMarked){
                document.getElementsByClassName("markedARGNode")[0].classList.remove("markedARGNode");
            }
            document.getElementById("arg-" + argElement).classList.add("markedARGNode");
            this.argNodeMarked = true;
        };

        //CFA-Controller
        this.setCFAFunction = function(value){
            this.selectedCFAFunction = value;
        };

        this.cfaFunctionIsSet = function(value){
            return value === this.selectedCFAFunction;
        };


        //Sections-Controller
        this.setWidth = function(event) {
            if (mouseDown) {
                wholeWidth = document.getElementById("externalFiles_section").offsetWidth + document.getElementById("errorpath_section").offsetWidth;
                document.getElementById("errorpath_section").style.width = (Math.round(event.clientX/wholeWidth*100) + "%");
                document.getElementById("externalFiles_section").style.width = (Math.round((wholeWidth - event.clientX)/wholeWidth*100) + "%");
            }
        };
        this.setMouseUp = function(){
            mouseDown = false;
            document.onselectstart = null;
            document.onmousedown = null;
        };


        //Tab-Controller
        this.setTab = function(value){
            this.tab = value;
        };

        this.tabIsSet = function(value){
            return this.tab === value;
        };


        //Code-Controller
        this.setMouseDown = function(){
            mouseDown = true;
            //werden benötigt, damit kein Text markiert wird beim Verschieben der middleline (übernommen aus altem)
            document.onselectstart = function(){return false;};
            document.onmousedown = function(){return false;};
        };

        this.setLine = function(id){
            if (this.selected_ErrLine != null) {
                document.getElementById(this.selected_ErrLine).style.outline = "none";
            }
            document.getElementById(id).style.outline = "red solid 1px";
            this.selected_ErrLine = id;
        };
    }]);
})();

var date = Date.now();
var logo = "http://cpachecker.sosy-lab.org/logo.svg";
var wholeWidth;
var mouseDown = false;
var functions = []; //FUNCTIONS
var fCallEdges = {}; //FCALLEDGES
var cfaInfo = {}; //CFAINFO
var errorPathData = {}; //ERRORPATH
var combinedNodes = {}; //COMBINEDNODES


function init(){
    var svgElements = document.getElementsByTagName("svg");
    //set IDs for all CFA-SVGs
    for(var i = 0; i<svgElements.length-1; i++){
        var graph = svgElements[i].getElementsByTagName("g")[0];
        graph.id = "cfaGraph-" + i.toString();
        var nodes = graph.getElementsByClassName("node");
        for(var k = 0; k < nodes.length; k++){
            nodes[k].id = "cfa-" + nodes[k].getElementsByTagName("title")[0].innerHTML;
        }
        var edges = graph.getElementsByClassName("edge");
        for(var l=0; l<edges.length; l++){
            var edge = edges[l];
            var fromto = [];
            if(edge.getElementsByTagName("title")[0].innerHTML.split("&#45;&gt;")[1] != null){
                fromto = edge.getElementsByTagName("title")[0].innerHTML.split("&#45;&gt;");
            } else if (edge.getElementsByTagName("title")[0].innerHTML.split("&#45;>")[1] != null) {
                fromto = edge.getElementsByTagName("title")[0].innerHTML.split("&#45;>");
            } else if (edge.getElementsByTagName("title")[0].innerHTML.split("-&gt;")[1] != null) {
                fromto = edge.getElementsByTagName("title")[0].innerHTML.split("-&gt;");
            } else {
                fromto = edge.getElementsByTagName("title")[0].innerHTML.split("->");
            }
            edge.id = "cfa-" + fromto[0] + "->" + fromto[1];
        }
    }
    //set IDs for the ARG-SVG
    graph = svgElements[svgElements.length-1].getElementsByTagName("g")[0];
    graph.id = "argGraph-" + (svgElements.length-1).toString();
    nodes = graph.getElementsByClassName("node");
    for(var n = 0; n < nodes.length; n++){
        nodes[n].id = "arg-" + nodes[n].getElementsByTagName("title")[0].innerHTML;
    }
    edges = graph.getElementsByClassName("edge");
    for(var o=0; o<edges.length; o++){
        edge = edges[o];
        fromto = [];
        if(edge.getElementsByTagName("title")[0].innerHTML.split("&#45;&gt;")[1] != null){
            fromto = edge.getElementsByTagName("title")[0].innerHTML.split("&#45;&gt;");
        } else if (edge.getElementsByTagName("title")[0].innerHTML.split("&#45;>")[1] != null) {
            fromto = edge.getElementsByTagName("title")[0].innerHTML.split("&#45;>");
        } else if (edge.getElementsByTagName("title")[0].innerHTML.split("-&gt;")[1] != null) {
            fromto = edge.getElementsByTagName("title")[0].innerHTML.split("-&gt;");
        } else {
            fromto = edge.getElementsByTagName("title")[0].innerHTML.split("->");
        }
        edge.id = "arg-" + fromto[0] + "->" + fromto[1];
    }

    //prepare Errorpath-Data for marking in the cfa
    var returnedges = {};
    for(key in fCallEdges){
        var fcalledge = fCallEdges[key];
        returnedges[fcalledge[1]] = fcalledge[0];
    }
    var errPathDataForCFA = [];
    for(var j = 0; j < errorPathData.length; j++){
        var source = errorPathData[j].source;
        var target = errorPathData[j].target;
        if(source in combinedNodes && target in combinedNodes){

        }
        else if(source in combinedNodes){
            errPathDataForCFA.push(combinedNodes[source] + "->" + target);
        }
        else if (source in fCallEdges){
            errPathDataForCFA.push(source + "->" + fCallEdges[source][0]);
        }
        else if (target in returnedges){
            errPathDataForCFA.push(returnedges[target] + "->" + target);
        } else {
            errPathDataForCFA.push(source + "->" + target);
        }
    }
    for(var m = 0; m < errPathDataForCFA.length; m++){
        var cfaEdge = document.getElementById("cfa-" + errPathDataForCFA[m]);
        var path = cfaEdge.getElementsByTagName("path")[0];
        var poly = cfaEdge.getElementsByTagName("polygon")[0];
        var text = cfaEdge.getElementsByTagName("text")[0];
        path.setAttribute("stroke", "#1e90ff");
        poly.setAttribute("stroke", "#1e90ff");
        poly.setAttribute("fill", "#1e90ff");
        if(text != undefined) {
            text.setAttribute("fill", "#1e90ff");
        }

    }

};
