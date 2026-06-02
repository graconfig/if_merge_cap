// MergeService — 接口合并分析サービス
//
// 入力：records JSON（GUI が持っている解析結果を直接送る／stateless）
// 出力：Job（非同期）/ MergeResult（JSON）

namespace handjapan.ifmerge;

using { handjapan.ifmerge.Job, handjapan.ifmerge.InterfaceRecordDto } from './analysis-service';

service MergeService @(path: '/merge', requires: 'ifmerge.api') {

    action merge(
        records : array of InterfaceRecordDto,
        options : MergeOptions
    ) returns Job;

    function getJob(id : UUID) returns Job;

    function getResult(id : UUID) returns MergeResult;
}

type MergeOptions {
    threshold : Decimal(3,2) default 0.80;
    mode      : String enum { max; avg } default 'max';
}

type MergeResult {
    summary           : MergeSummary;
    groups            : array of MergeGroupDto;
    similarityMatrices: array of ModuleSimilarityMatrix;
}

type MergeSummary {
    totalInterfaces     : Integer;
    totalGroups         : Integer;
    mergeableGroups     : Integer;
    savedInterfaceCount : Integer;
    modules             : array of String;
}

type MergeGroupDto {
    groupingId       : String;
    module           : String;
    scenario         : String;
    members          : array of GroupMemberDto;
    mergeRequired    : Boolean;
    mergedIfName     : String;
    mergedFieldCount : Integer;
    mergedFields     : array of MergedFieldDto;
}

type GroupMemberDto {
    ifName              : String;
    documentNumber      : String;
    itemCount           : Integer;
    ifSummary           : String;
    representativeItems : array of String;
    groupingReason      : String;
}

type MergedFieldDto {
    no            : Integer;
    ebsTableId    : String;
    ebsTableName  : String;
    itemId        : String;
    itemName      : String;
}

type ModuleSimilarityMatrix {
    module    : String;
    scenarios : array of ScenarioMatrix;
}

type ScenarioMatrix {
    scenario              : String;
    axis                  : MatrixAxis;
    maxSimilarity         : array of array of Double;
    directionalSimilarity : array of array of DirectionalValue;
}

type MatrixAxis {
    ifNames         : array of String;
    documentNumbers : array of String;
}

type DirectionalValue {
    rowToCol : Double;
    colToRow : Double;
}
