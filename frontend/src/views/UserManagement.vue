<template>
  <div class="user-management">
    <div class="card-container">
      <div class="page-header">
        <span class="page-title">用户管理</span>
        <a-space>
          <a-input
            v-model:value="searchKeyword"
            placeholder="搜索用户名/真实姓名"
            style="width: 240px"
            allow-clear
          >
            <template #prefix>
              <SearchOutlined />
            </template>
          </a-input>
          <a-button type="primary" @click="addUser">
            <PlusOutlined />
            新增用户
          </a-button>
        </a-space>
      </div>

      <a-table
        :columns="userColumns"
        :data-source="filteredUsers"
        :pagination="{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `共 ${total} 条` }"
        :loading="loading"
        row-key="id"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'roles'">
            <a-tag
              v-for="role in record.roleCodes"
              :key="role"
              :color="getRoleColor(role)"
              style="margin-right: 4px"
            >
              {{ getRoleName(role) }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-badge
              :status="record.enabled ? 'success' : 'error'"
              :text="record.enabled ? '启用' : '禁用'"
            />
          </template>
          <template v-else-if="column.key === 'lastLoginTime'">
            {{ record.lastLoginTime || '--' }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" @click="editUser(record)">
                编辑
              </a-button>
              <a-button type="link" @click="resetPassword(record)">
                重置密码
              </a-button>
              <a-popconfirm
                title="确认删除此用户？"
                ok-text="确认删除"
                ok-type="danger"
                cancel-text="取消"
                @confirm="deleteUser(record.id)"
              >
                <a-button type="link" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑用户' : '新增用户'"
      @ok="handleSubmit"
      @cancel="handleCancel"
      :confirm-loading="submitting"
      width="560px"
    >
      <a-form
        ref="formRef"
        :model="userForm"
        layout="vertical"
        label-col={{ span: 6 }}
        wrapper-col={{ span: 18 }}
      >
        <a-form-item
          label="用户名"
          name="username"
          :rules="[{ required: true, message: '请输入用户名' }]"
        >
          <a-input v-model:value="userForm.username" placeholder="请输入用户名" :disabled="isEdit" />
        </a-form-item>
        <a-form-item
          v-if="!isEdit"
          label="密码"
          name="password"
          :rules="[{ required: true, message: '请输入密码' }]"
        >
          <a-input-password v-model:value="userForm.password" placeholder="请输入密码" />
        </a-form-item>
        <a-form-item
          label="真实姓名"
          name="realName"
          :rules="[{ required: true, message: '请输入真实姓名' }]"
        >
          <a-input v-model:value="userForm.realName" placeholder="请输入真实姓名" />
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="邮箱" name="email">
              <a-input v-model:value="userForm.email" placeholder="请输入邮箱" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="电话" name="phone">
              <a-input v-model:value="userForm.phone" placeholder="请输入电话" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-form-item
          label="角色"
          name="roleIds"
          :rules="[{ required: true, message: '请选择角色' }]"
        >
          <a-select
            v-model:value="userForm.roleIds"
            mode="multiple"
            placeholder="请选择角色"
          >
            <a-select-option
              v-for="role in availableRoles"
              :key="role.id"
              :value="role.id"
            >
              {{ role.roleName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="启用状态" name="enabled">
          <a-switch v-model:checked="userForm.enabled" checked-children="启用" un-checked-children="禁用" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="resetPasswordVisible"
      title="重置密码"
      @ok="handleResetPassword"
      @cancel="resetPasswordVisible = false"
      :confirm-loading="resetPasswordLoading"
      width="420px"
    >
      <a-form
        ref="resetPasswordFormRef"
        :model="resetPasswordForm"
        layout="vertical"
      >
        <a-form-item label="当前用户">
          <a-tag color="blue">{{ resetPasswordTarget?.username }}</a-tag>
          <span style="margin-left: 8px">{{ resetPasswordTarget?.realName }}</span>
        </a-form-item>
        <a-form-item
          label="新密码"
          name="password"
          :rules="[
            { required: true, message: '请输入新密码' },
            { min: 6, message: '密码长度不能少于6位' }
          ]"
        >
          <a-input-password v-model:value="resetPasswordForm.password" placeholder="请输入新密码" />
        </a-form-item>
        <a-form-item
          label="确认密码"
          name="confirmPassword"
          :rules="[
            { required: true, message: '请再次输入新密码' },
            { validator: validateConfirmPassword }
          ]"
        >
          <a-input-password v-model:value="resetPasswordForm.confirmPassword" placeholder="请再次输入新密码" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue'
import { useAppStore } from '@/stores'
import { storeToRefs } from 'pinia'
import { userApi } from '@/api'
import type { User, Role } from '@/types'
import {
  SearchOutlined,
  PlusOutlined
} from '@ant-design/icons-vue'

const { loading } = storeToRefs(useAppStore())

const searchKeyword = ref('')
const users = ref<User[]>([])
const availableRoles = ref<Role[]>([])
const modalVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const resetPasswordVisible = ref(false)
const resetPasswordLoading = ref(false)
const resetPasswordFormRef = ref<FormInstance>()
const resetPasswordTarget = ref<User | null>(null)

const userForm = reactive({
  id: '',
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
  roleIds: [] as string[],
  enabled: true
})

const resetPasswordForm = reactive({
  password: '',
  confirmPassword: ''
})

const userColumns = [
  { title: '用户名', dataIndex: 'username', key: 'username', width: 140 },
  { title: '真实姓名', dataIndex: 'realName', key: 'realName', width: 120 },
  { title: '邮箱', dataIndex: 'email', key: 'email', ellipsis: true },
  { title: '电话', dataIndex: 'phone', key: 'phone', width: 130 },
  { title: '角色', key: 'roles', width: 180 },
  { title: '状态', key: 'enabled', width: 100 },
  { title: '最后登录时间', key: 'lastLoginTime', width: 180 },
  { title: '操作', key: 'action', width: 220, fixed: 'right' }
]

const filteredUsers = computed(() => {
  if (!searchKeyword.value) return users.value
  const keyword = searchKeyword.value.toLowerCase()
  return users.value.filter(u =>
    u.username.toLowerCase().includes(keyword) ||
    u.realName.toLowerCase().includes(keyword)
  )
})

function getRoleColor(roleCode: string): string {
  const colors: Record<string, string> = {
    admin: 'red',
    operator: 'blue',
    viewer: 'default'
  }
  return colors[roleCode] || 'default'
}

function getRoleName(roleCode: string): string {
  const names: Record<string, string> = {
    admin: '管理员',
    operator: '操作员',
    viewer: '只读用户'
  }
  return names[roleCode] || roleCode
}

async function fetchUsers() {
  try {
    loading.value = true
    users.value = await userApi.getAll()
  } catch (e) {
    message.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

async function fetchRoles() {
  try {
    availableRoles.value = await userApi.getAllRoles()
  } catch (e) {
    message.error('获取角色列表失败')
  }
}

function addUser() {
  isEdit.value = false
  Object.assign(userForm, {
    id: '',
    username: '',
    password: '',
    realName: '',
    email: '',
    phone: '',
    roleIds: [],
    enabled: true
  })
  modalVisible.value = true
}

function editUser(user: User) {
  isEdit.value = true
  Object.assign(userForm, {
    id: user.id,
    username: user.username,
    password: '',
    realName: user.realName,
    email: user.email || '',
    phone: user.phone || '',
    roleIds: availableRoles.value
      .filter(r => user.roleCodes?.includes(r.roleCode))
      .map(r => r.id),
    enabled: user.enabled
  })
  modalVisible.value = true
}

function resetPassword(user: User) {
  resetPasswordTarget.value = user
  resetPasswordForm.password = ''
  resetPasswordForm.confirmPassword = ''
  resetPasswordVisible.value = true
}

const validateConfirmPassword: Rule['validator'] = async (_rule, value) => {
  if (value !== resetPasswordForm.password) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
    submitting.value = true
    if (isEdit.value) {
      await userApi.update(userForm.id, {
        realName: userForm.realName,
        email: userForm.email || undefined,
        phone: userForm.phone || undefined,
        enabled: userForm.enabled,
        roleIds: userForm.roleIds
      })
      message.success('用户更新成功')
    } else {
      await userApi.create({
        username: userForm.username,
        password: userForm.password,
        realName: userForm.realName,
        email: userForm.email || undefined,
        phone: userForm.phone || undefined,
        enabled: userForm.enabled,
        roleIds: userForm.roleIds
      })
      message.success('用户添加成功')
    }
    modalVisible.value = false
    await fetchUsers()
  } catch (e: any) {
    if (e?.errorFields) return
    message.error(isEdit.value ? '更新用户失败' : '添加用户失败')
  } finally {
    submitting.value = false
  }
}

function handleCancel() {
  modalVisible.value = false
}

async function handleResetPassword() {
  try {
    await resetPasswordFormRef.value?.validate()
    resetPasswordLoading.value = true
    await userApi.resetPassword(resetPasswordTarget.value!.id, resetPasswordForm.password)
    message.success('密码重置成功')
    resetPasswordVisible.value = false
  } catch (e: any) {
    if (e?.errorFields) return
    message.error('密码重置失败')
  } finally {
    resetPasswordLoading.value = false
  }
}

async function deleteUser(id: string) {
  try {
    await userApi.delete(id)
    message.success('删除成功')
    await fetchUsers()
  } catch (e) {
    message.error('删除失败')
  }
}

onMounted(async () => {
  await Promise.all([
    fetchUsers(),
    fetchRoles()
  ])
})
</script>

<style scoped>
.user-management {
  padding: 20px;
}

.card-container {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #262626;
}
</style>
