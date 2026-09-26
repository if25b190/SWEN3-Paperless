"use client";

import React, { useState, useEffect, useCallback } from "react";
import { ProtectedRoute } from "@/components/ProtectedRoute";
import { useTranslation } from "@/lib/i18n/context";
import { documentTypesApi, ApiError } from "@/lib/api/client";
import { DocumentTypeResponse } from "@/lib/types/api";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import {
  Tag,
  Plus,
  Edit,
  Trash2,
  Loader2,
  MoreVertical,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { toast } from "sonner";

export default function DocumentTypesPage() {
  const { t } = useTranslation();

  const [documentTypes, setDocumentTypes] = useState<DocumentTypeResponse[]>([]);
  const [loading, setLoading] = useState(true);

  // Create Dialog
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createDescription, setCreateDescription] = useState("");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Edit Dialog
  const [editingType, setEditingType] = useState<DocumentTypeResponse | null>(null);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editing, setEditing] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  // Delete Dialog
  const [deletingType, setDeletingType] = useState<DocumentTypeResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  const fetchDocumentTypes = useCallback(async () => {
    setLoading(true);
    try {
      const res = await documentTypesApi.getDocumentTypes();
      setDocumentTypes(res.items || []);
    } catch {
      // Handled by api client
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDocumentTypes();
  }, [fetchDocumentTypes]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError(null);

    if (!createName.trim()) {
      setCreateError("Name cannot be empty.");
      return;
    }

    setCreating(true);
    try {
      await documentTypesApi.createDocumentType({
        name: createName.trim(),
        description: createDescription.trim() || null,
      });
      toast.success(t.documentTypes.createSuccess);
      setCreateDialogOpen(false);
      setCreateName("");
      setCreateDescription("");
      fetchDocumentTypes();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setCreateError(err.message);
      } else {
        setCreateError("Failed to create document type.");
      }
    } finally {
      setCreating(false);
    }
  };

  const handleEdit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingType) return;
    setEditError(null);

    if (!editName.trim()) {
      setEditError("Name cannot be empty.");
      return;
    }

    setEditing(true);
    try {
      await documentTypesApi.updateDocumentType(editingType.id, {
        name: editName.trim(),
        description: editDescription.trim() || null,
      });
      toast.success(t.documentTypes.editSuccess);
      setEditingType(null);
      fetchDocumentTypes();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setEditError(err.message);
      } else {
        setEditError("Failed to update document type.");
      }
    } finally {
      setEditing(false);
    }
  };

  const handleDelete = async () => {
    if (!deletingType) return;
    setDeleting(true);
    try {
      await documentTypesApi.deleteDocumentType(deletingType.id);
      toast.success(t.documentTypes.deleteSuccess);
      setDeletingType(null);
      fetchDocumentTypes();
    } catch {
      // Handled by api client
    } finally {
      setDeleting(false);
    }
  };

  return (
    <ProtectedRoute>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              {t.documentTypes.title}
            </h1>
            <p className="text-sm text-slate-500">{t.documentTypes.subtitle}</p>
          </div>
          <Button
            onClick={() => setCreateDialogOpen(true)}
            className="bg-blue-600 hover:bg-blue-700 text-white gap-2 font-medium"
          >
            <Plus className="h-4 w-4" />
            {t.documentTypes.createType}
          </Button>
        </div>

        {/* Content Table */}
        {loading ? (
          <div className="flex flex-col items-center justify-center p-12 bg-white rounded-xl border border-slate-200">
            <Loader2 className="h-8 w-8 animate-spin text-blue-600 mb-2" />
            <p className="text-sm text-slate-500">{t.common.loading}</p>
          </div>
        ) : documentTypes.length === 0 ? (
          <div className="flex flex-col items-center justify-center p-12 bg-white rounded-xl border border-slate-200 text-center">
            <div className="h-12 w-12 rounded-full bg-slate-100 flex items-center justify-center text-slate-400 mb-3">
              <Tag className="h-6 w-6" />
            </div>
            <h3 className="text-base font-semibold text-slate-900">
              {t.documentTypes.noTypesFound}
            </h3>
            <p className="text-sm text-slate-500 max-w-sm mt-1 mb-4">
              Create categories such as Invoice, Contract, Receipt, or Bank Statement to organize files.
            </p>
            <Button
              onClick={() => setCreateDialogOpen(true)}
              className="bg-blue-600 hover:bg-blue-700 text-white gap-2 font-medium"
            >
              <Plus className="h-4 w-4" />
              {t.documentTypes.createType}
            </Button>
          </div>
        ) : (
          <div className="bg-white rounded-xl border border-slate-200 shadow-xs overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  <tr>
                    <th className="py-3.5 px-6">{t.documentTypes.typeName}</th>
                    <th className="py-3.5 px-6">{t.documentTypes.typeDescription}</th>
                    <th className="py-3.5 px-6 text-right">{t.common.actions}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {documentTypes.map((dt) => (
                    <tr
                      key={dt.id}
                      className="hover:bg-slate-50/75 transition-colors"
                    >
                      <td className="py-3.5 px-6 font-semibold text-slate-900 flex items-center gap-2">
                        <Tag className="h-4 w-4 text-blue-600 shrink-0" />
                        <span>{dt.name}</span>
                      </td>
                      <td className="py-3.5 px-6 text-slate-600 text-xs sm:text-sm">
                        {dt.description || "-"}
                      </td>
                      <td className="py-3.5 px-6 text-right">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-8 w-8 text-slate-500 hover:text-slate-900"
                            >
                              <MoreVertical className="h-4 w-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="w-40">
                            <DropdownMenuItem
                              onClick={() => {
                                setEditingType(dt);
                                setEditName(dt.name);
                                setEditDescription(dt.description || "");
                                setEditError(null);
                              }}
                              className="gap-2 cursor-pointer"
                            >
                              <Edit className="h-4 w-4 text-slate-500" />
                              <span>{t.common.edit}</span>
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem
                              onClick={() => setDeletingType(dt)}
                              className="gap-2 text-red-600 focus:text-red-700 focus:bg-red-50 cursor-pointer"
                            >
                              <Trash2 className="h-4 w-4" />
                              <span>{t.common.delete}</span>
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* Create Dialog */}
        <Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>{t.documentTypes.createTitle}</DialogTitle>
            </DialogHeader>

            {createError && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                {createError}
              </div>
            )}

            <form onSubmit={handleCreate} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="create-dt-name">
                  {t.documentTypes.typeName} *
                </Label>
                <Input
                  id="create-dt-name"
                  value={createName}
                  onChange={(e) => setCreateName(e.target.value)}
                  placeholder={t.documentTypes.typeNamePlaceholder}
                  required
                  maxLength={100}
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="create-dt-desc">
                  {t.documentTypes.typeDescription}
                </Label>
                <Textarea
                  id="create-dt-desc"
                  value={createDescription}
                  onChange={(e) => setCreateDescription(e.target.value)}
                  placeholder={t.documentTypes.typeDescriptionPlaceholder}
                />
              </div>

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setCreateDialogOpen(false)}
                  disabled={creating}
                >
                  {t.common.cancel}
                </Button>
                <Button
                  type="submit"
                  disabled={creating || !createName.trim()}
                  className="bg-blue-600 hover:bg-blue-700 text-white font-medium"
                >
                  {creating ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      {t.common.loading}
                    </>
                  ) : (
                    t.documentTypes.createType
                  )}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>

        {/* Edit Dialog */}
        <Dialog
          open={!!editingType}
          onOpenChange={(open) => !open && setEditingType(null)}
        >
          <DialogContent className="sm:max-w-md">
            <DialogHeader>
              <DialogTitle>{t.documentTypes.editType}</DialogTitle>
            </DialogHeader>

            {editError && (
              <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                {editError}
              </div>
            )}

            <form onSubmit={handleEdit} className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="edit-dt-name">
                  {t.documentTypes.typeName} *
                </Label>
                <Input
                  id="edit-dt-name"
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  required
                  maxLength={100}
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="edit-dt-desc">
                  {t.documentTypes.typeDescription}
                </Label>
                <Textarea
                  id="edit-dt-desc"
                  value={editDescription}
                  onChange={(e) => setEditDescription(e.target.value)}
                />
              </div>

              <DialogFooter className="pt-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setEditingType(null)}
                  disabled={editing}
                >
                  {t.common.cancel}
                </Button>
                <Button
                  type="submit"
                  disabled={editing || !editName.trim()}
                  className="bg-blue-600 hover:bg-blue-700 text-white font-medium"
                >
                  {editing ? (
                    <>
                      <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                      {t.common.loading}
                    </>
                  ) : (
                    t.common.save
                  )}
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>

        {/* Delete Confirmation Dialog */}
        <ConfirmDialog
          open={!!deletingType}
          onOpenChange={(open) => !open && setDeletingType(null)}
          title={t.documentTypes.deleteConfirmTitle}
          description={t.documentTypes.deleteConfirmText}
          confirmLabel={t.common.delete}
          loading={deleting}
          onConfirm={handleDelete}
        />
      </div>
    </ProtectedRoute>
  );
}
