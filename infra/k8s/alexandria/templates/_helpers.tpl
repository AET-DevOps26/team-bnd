{{- define "alexandria.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "alexandria.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "alexandria.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "alexandria.labels" -}}
helm.sh/chart: {{ include "alexandria.chart" . }}
{{ include "alexandria.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{- define "alexandria.selectorLabels" -}}
app.kubernetes.io/name: {{ include "alexandria.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "alexandria.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "alexandria.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "alexandria.image" -}}
{{ .Values.image.registry }}/{{ .Values.image.repository }}/{{ .service }}:{{ .Values.image.tag }}
{{- end }}

{{- define "alexandria.s3Endpoint" -}}
http://{{ .Release.Name }}-seaweedfs-s3:{{ .Values.seaweedfs.s3.port }}
{{- end }}
